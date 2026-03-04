package com.cleanroute.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OpenMeteoAqiResponse {
    
    private Hourly hourly;

    public Hourly getHourly() {
        return hourly;
    }

    public void setHourly(Hourly hourly) {
        this.hourly = hourly;
    }

    public static class Hourly {
        private List<String> time;
        private List<Double> european_aqi;
        private List<Double> pm10;
        private List<Double> pm2_5;
        private List<Double> carbon_monoxide;
        private List<Double> nitrogen_dioxide;

        public List<String> getTime() {
            return time;
        }

        public void setTime(List<String> time) {
            this.time = time;
        }

        public List<Double> getEuropean_aqi() {
            return european_aqi;
        }

        public void setEuropean_aqi(List<Double> european_aqi) {
            this.european_aqi = european_aqi;
        }

        public List<Double> getPm10() {
            return pm10;
        }

        public void setPm10(List<Double> pm10) {
            this.pm10 = pm10;
        }

        public List<Double> getPm2_5() {
            return pm2_5;
        }

        public void setPm2_5(List<Double> pm2_5) {
            this.pm2_5 = pm2_5;
        }

        public List<Double> getCarbon_monoxide() {
            return carbon_monoxide;
        }

        public void setCarbon_monoxide(List<Double> carbon_monoxide) {
            this.carbon_monoxide = carbon_monoxide;
        }

        public List<Double> getNitrogen_dioxide() {
            return nitrogen_dioxide;
        }

        public void setNitrogen_dioxide(List<Double> nitrogen_dioxide) {
            this.nitrogen_dioxide = nitrogen_dioxide;
        }
    }
}
