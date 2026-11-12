---
title: How ClearWay scores routes and sends alerts
source_url: https://github.com/bhuvii2005/ClearWay-app
---

# How ClearWay scores routes and sends alerts

## Getting the routes

When you plan a trip, ClearWay asks TomTom for up to three real street routes between your start and end points, for walking, jogging, cycling or driving.

## Sampling the air along a route

ClearWay does not just check the air at the start and the end. It picks up to 10 sample points spread along each route and asks Open-Meteo for the air quality at each point. Each reading includes the European AQI and the levels of PM2.5, PM10, NO₂, O₃ and CO.

## The score

Each sample point gets a score equal to its European AQI. If you have told ClearWay you are sensitive to a pollutant (PM2.5, PM10, NO₂ or ozone), that pollutant's level adds extra weight to the point's score. The route's exposure score is the average of its point scores, and routes are ranked by it, so the lowest score, the cleanest route, comes first. ClearWay also shows how each pollutant compares with the WHO guideline levels, and how much time and distance a cleaner route adds. If the air-quality service cannot be reached, the route has no score rather than a made-up one.

## How precise the data is

The air-quality data comes from Open-Meteo's forecast models, which work on a grid with cells roughly 10 to 40 kilometres across, depending on the region. That means two routes that are close together may get very similar readings, and the data cannot see street-level differences such as one side of a busy road versus a quiet lane. Treat the scores as a guide to the air in the area, not an exact measurement of the air on each street.

## Alerts on saved routes

You can save routes you use often. Every 15 minutes ClearWay checks the air on saved routes in the background and records each check, so you can see a route's history over time. It sends two kinds of alert in the app:

- **Poor air:** when a route's score goes above 75, which is in the Poor band of the European AQI.
- **Unusually clean air:** when a route's score is at least 30% below its usual average, a good time to go.

To avoid repeating itself, ClearWay sends at most one alert per route every 2 hours.

A route that has just been saved has no history yet; its history fills in as the background checks run.
