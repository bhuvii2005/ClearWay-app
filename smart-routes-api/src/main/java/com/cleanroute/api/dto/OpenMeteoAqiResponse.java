package com.cleanroute.api.dto;

/**
 * Subset of the Open-Meteo Air Quality API response used by ClearWay.
 * Requested with "current=european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone",
 * so Open-Meteo itself picks the reading for the current hour at that location.
 * Field names match the JSON keys exactly.
 */
public class OpenMeteoAqiResponse {

    public Current current;

    public static class Current {
        public String time;
        public Double european_aqi;
        public Double pm10;
        public Double pm2_5;
        public Double carbon_monoxide;
        public Double nitrogen_dioxide;
        public Double ozone;
    }
}
