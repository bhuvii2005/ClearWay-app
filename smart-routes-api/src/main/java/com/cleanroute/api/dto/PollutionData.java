package com.cleanroute.api.dto;

public class PollutionData {
    private double aqi;
    private double pm25;
    private double pm10;
    private double no2;
    private double ozone;
    private double co;

    public PollutionData() {}

    public PollutionData(double aqi, double pm25, double pm10, double no2, double ozone, double co) {
        this.aqi = aqi;
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.no2 = no2;
        this.ozone = ozone;
        this.co = co;
    }

    public double getAqi() { return aqi; }
    public void setAqi(double aqi) { this.aqi = aqi; }

    public double getPm25() { return pm25; }
    public void setPm25(double pm25) { this.pm25 = pm25; }

    public double getPm10() { return pm10; }
    public void setPm10(double pm10) { this.pm10 = pm10; }

    public double getNo2() { return no2; }
    public void setNo2(double no2) { this.no2 = no2; }

    public double getOzone() { return ozone; }
    public void setOzone(double ozone) { this.ozone = ozone; }

    public double getCo() { return co; }
    public void setCo(double co) { this.co = co; }
}
