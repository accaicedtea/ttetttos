package com.myapp.kiosk

    import android.app.Application
    import android.os.Build
    import android.os.Bundle
    import android.view.View
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.foundation.background
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.input.PasswordVisualTransformation
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import androidx.lifecycle.viewmodel.compose.viewModel
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.launch
    import okhttp3.*
    import okhttp3.MediaType.Companion.toMediaTypeOrNull
    import okhttp3.RequestBody.Companion.toRequestBody
    import org.json.JSONObject
    import java.io.IOException

    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            
            // --- 1. SCHERMO INTERO (Immersive Mode) ---
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFF5F5F5)
                    ) {
                        val viewModel: KdsViewModel = viewModel()
                        val isLoggedIn by viewModel.isLoggedIn.collectAsState()

                        if (!isLoggedIn) {
                            // Schermata di inserimento PIN
                            LoginScreen(viewModel)
                        } else {
                            // La tua schermata degli ordini esistente
                            KdsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    // Schermata per inserire il PIN
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LoginScreen(viewModel: KdsViewModel) {
        var pin by remember { mutableStateOf("") }
        val loginError by viewModel.loginError.collectAsState()

        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.width(350.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Autorizzazione KDS", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Inserisci PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (loginError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(loginError, color = Color.Red, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.verifyPin(pin) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                    ) {
                        Text("ACCEDI", fontSize = 18.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // ==========================================
    // MODELS
    // ==========================================
    data class OrderItem(
        val productName: String,
        val quantity: Int,
        val ingredients: List<String>
    )

    data class Order(
        val orderNumber: String,
        val items: List<OrderItem>,
        val totale: Double = 0.0,
        val time: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    ) {
        val summary: String get() = if (items.size == 1) {
            "${items.first().quantity}x ${items.first().productName}"
        } else {
            "${items.size} prodotti"
        }
    }

    // ==========================================
    // VIEWMODEL (Gestione WebSocket, Stato e Notifiche)
    // ==========================================
    class KdsViewModel(application: Application) : AndroidViewModel(application) {
        // ---- IP e URL PER IL TUO CODEIGNITER ----
        private val API_URL = "http://192.168.1.100/api/v1/totem/kds/login" 
    
        // (Conserva i tuoi url correnti per i token / WebSocket)
        private val serverUrl = "ws://192.168.1.100:7070/api/ws/orders"
        private val client = OkHttpClient.Builder()
            .pingInterval(10, TimeUnit.SECONDS)
            .build()

        private var webSocket: WebSocket? = null

        private val _isLoggedIn = MutableStateFlow(false)
        val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

        private val _loginError = MutableStateFlow("")
        val loginError: StateFlow<String> = _loginError

        private val _orders = MutableStateFlow<List<Order>>(emptyList())
        val orders: StateFlow<List<Order>> = _orders

        private val _connectionStatus = MutableStateFlow("In connessione...")
        val connectionStatus: StateFlow<String> = _connectionStatus

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        private val _selectedOrder = MutableStateFlow<Order?>(null)
        val selectedOrder: StateFlow<Order?> = _selectedOrder

        
        fun verifyPin(pin: String) {
            val payload = JSONObject().apply {
                put("action", "kds_login")
                put("pin", pin)
            }
            webSocket?.send(payload.toString())
        }

        fun selectOrder(order: Order) {
            _selectedOrder.value = order
        }

        init {
            createNotificationChannel()
            connectWebSocket()
            startApplicationPing()
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "order_channel",
                    "Nuovi Ordini",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifica quando arriva un nuovo ordine"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
                val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun showNotification(order: Order) {
            val context = getApplication<Application>()
            val itemsText = order.items.joinToString(", ") { "${it.quantity}x ${it.productName}" }

            val notification = NotificationCompat.Builder(context, "order_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🆕 Nuovo Ordine ${order.orderNumber}")
                .setContentText(itemsText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(order.orderNumber.hashCode(), notification)
        }

        private fun connectWebSocket() {
            _connectionStatus.value = "Connessione a $serverUrl..."
            val request = Request.Builder().url(serverUrl).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _isConnected.value = true
                    _connectionStatus.value = "🟢 Connesso al Totem via Wi-Fi"
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        if (text.contains(""""action":"login_response""")) {
                            val obj = JSONObject(text)
                            val success = obj.optBoolean("success", false)
                            if (success) {
                                _isLoggedIn.value = true
                                _loginError.value = ""
                            } else {
                                _loginError.value = obj.optString("message", "PIN errato")
                            }
                            return
                        }
                        val obj = JSONObject(text)
                        
                        if (obj.has("action") && obj.getString("action") == "login_response") {
                            val success = obj.optBoolean("success", false)
                            if (success) {
                                _isLoggedIn.value = true
                                _loginError.value = ""
                            } else {
                                _loginError.value = obj.optString("message", "PIN errato")
                            }
                            return
                        }

                    try {
                        val element = org.json.JSONTokener(text).nextValue()
                        if (element is JSONObject) {
                            if (element.optString("action") == "pong") return
                        } else if (element is JSONArray) {
                            val parsedOrders = mutableListOf<Order>()
                            for (i in 0 until element.length()) {
                                val obj = element.getJSONObject(i)
                                
                                val itemsArray = obj.optJSONArray("prodotti") ?: obj.optJSONArray("dettaglio")
                                val rawItems = mutableListOf<OrderItem>()

                                if (itemsArray != null) {
                                    for (j in 0 until itemsArray.length()) {
                                        val itemObj = itemsArray.getJSONObject(j)
                                        val name = itemObj.optString("nome", itemObj.optString("id_prodotto", "Sconosciuto"))
                                        val qty = itemObj.optInt("quantita", itemObj.optInt("qta", 1))
                                        
                                        val ingArray = itemObj.optJSONArray("ingredienti")
                                        val ingredients = mutableListOf<String>()
                                        if (ingArray != null) {
                                            for (k in 0 until ingArray.length()) {
                                                val ingObj = ingArray.getJSONObject(k)
                                                val ingName = ingObj.optString("nome", "")
                                                if (ingName.isNotEmpty()) {
                                                    ingredients.add(ingName)
                                                }
                                            }
                                        }
                                        rawItems.add(OrderItem(name, qty, ingredients))
                                    }
                                }

                                val groupedMap = mutableMapOf<String, OrderItem>()
                                for (item in rawItems) {
                                    val sortedIng = item.ingredients.sorted().joinToString("|")
                                    val key = "${item.productName}::$sortedIng"
                                    if (groupedMap.containsKey(key)) {
                                        val existing = groupedMap[key]!!
                                        groupedMap[key] = existing.copy(quantity = existing.quantity + item.quantity)
                                    } else {
                                        groupedMap[key] = item
                                    }
                                }

                                val orderIdStr = obj.optString("tablet_order_id", "")
                                parsedOrders.add(
                                    Order(
                                        orderNumber = orderIdStr,
                                        totale = obj.optDouble("totale", 0.0),
                                        items = groupedMap.values.toList()
                                    )
                                )
                            }
                            
                            // Gestione auto-selezione
                            if (_selectedOrder.value == null && parsedOrders.isNotEmpty()) {
                                _selectedOrder.value = parsedOrders.first()
                            } else if (_selectedOrder.value != null && parsedOrders.none { it.orderNumber == _selectedOrder.value!!.orderNumber }) {
                                _selectedOrder.value = parsedOrders.firstOrNull()
                            } else if (_selectedOrder.value != null) {
                                _selectedOrder.value = parsedOrders.find { it.orderNumber == _selectedOrder.value!!.orderNumber }
                            }

                            _orders.value = parsedOrders
                            
                            // Mostra notifica per i nuovi arrivati
                            val oldIds = _orders.value.map { it.orderNumber }.toSet()
                            val newOrders = parsedOrders.filter { it.orderNumber.isNotEmpty() && it.orderNumber !in oldIds }
                            newOrders.forEach { showNotification(it) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleDisconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleDisconnect()
                }
            })
        }

        private fun handleDisconnect() {
            if (!_isConnected.value) return
            _isConnected.value = false
            _connectionStatus.value = "🔴 Disconnesso. Riconnessione..."
            viewModelScope.launch {
                // Retry exponenziale: 2s, 4s, 8s, 16s, 30s, 30s, ...
                var retryDelay = 2000L
                while (!_isConnected.value) {
                    delay(retryDelay)
                    connectWebSocket()
                    retryDelay = minOf(retryDelay * 2, 30000L)
                    delay(500) // Piccolo delay per dare tempo al webSocket di stabilirsi
                }
            }
        }

        private fun startApplicationPing() {
            viewModelScope.launch {
                while (true) {
                    delay(10000)
                    if (_isConnected.value) {
                        val pingJson = JSONObject().apply { put("action", "ping") }
                        webSocket?.send(pingJson.toString())
                    }
                }
            }
        }

        fun takeOrder(orderId: String) {
            val payload = JSONObject().apply {
                put("action", "take_order")
                put("orderId", orderId)
            }
            webSocket?.send(payload.toString())
        }

        override fun onCleared() {
            webSocket?.close(1000, "App closed")
            super.onCleared()
        }
    }

    // ==========================================
    // COMPOSE UI
    // ==========================================
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun KdsScreen(viewModel: KdsViewModel = viewModel()) {
        val orders by viewModel.orders.collectAsState()
        val status by viewModel.connectionStatus.collectAsState()
        val isConnected by viewModel.isConnected.collectAsState()
        val selectedOrder by viewModel.selectedOrder.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("KDS Cucine / Bar", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF333333)
                    )
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                // PANNELLO SINISTRO: LISTA ORDINI COMPATTA (1/3)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFEEEEEE))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = status,
                            color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (orders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nessuna coda", fontSize = 18.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(orders) { order ->
                                CompactOrderCard(
                                    order = order,
                                    isSelected = selectedOrder?.orderNumber == order.orderNumber,
                                    onClick = { viewModel.selectOrder(order) }
                                )
                            }
                        }
                    }
                }

                // PANNELLO DESTRO: DETTAGLIO ORDINE CORRENTE (2/3)
                Column(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .background(Color.White)
                ) {
                    if (selectedOrder == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Seleziona un ordine per vedere i dettagli", fontSize = 20.sp, color = Color.Gray)
                        }
                    } else {
                        OrderDetailView(
                            order = selectedOrder!!, 
                            onTakeOrder = { viewModel.takeOrder(it) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun CompactOrderCard(order: Order, isSelected: Boolean, onClick: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "#${order.orderNumber}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = order.time,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = order.summary,
                    fontSize = 15.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    fun OrderDetailView(order: Order, onTakeOrder: (String) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(28.dp)
        ) {
            // HEADER: Numero ordine piccolo + Orario
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Ordine #${order.orderNumber}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = order.time,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(20.dp))

            // LISTA PRODOTTI E INGREDIENTI (scrollabile)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(order.items.size) { index ->
                    val item = order.items[index]
                    Column {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Badge quantità
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2196F3), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${item.quantity}x",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                                
                                Text(
                                    text = item.productName,
                                    fontSize = 18.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            if (item.ingredients.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .padding(start = 48.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item.ingredients.forEach { ingred ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFFF8E1), RoundedCornerShape(18.dp))
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ingred,
                                                fontSize = 13.sp,
                                                color = Color(0xFFE65100),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Separatore tra prodotti (tranne l'ultimo)
                        if (index < order.items.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFDDDDDD))
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(20.dp))

            // FOOTER: Totale + Pulsanti azione
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = String.format("Totale: € %.2f", order.totale),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.Black
                )

                // DUE PULSANTI: PREPARA e COMPLETA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onTakeOrder(order.orderNumber) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "🔄 PREPARA",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Button(
                        onClick = { onTakeOrder(order.orderNumber + "-done") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "✓ COMPLETA",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }