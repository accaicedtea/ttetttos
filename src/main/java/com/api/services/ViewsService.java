package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;

public class ViewsService {
    public static JsonObject getMenu()              throws Exception { return Api.apiGet("menu");              }
    public static JsonObject getMenuVersion()       throws Exception { return Api.apiGet("versione");          }
    public static JsonObject getProductById(int id) throws Exception { return Api.apiGet("prodotti/" + id);    }
}
