# Requirements Document

## Functional Requirements
1. **Login**: Users should be able to create an account and login securely.
2. **Checking routes**: You can use the app to check random routes on the fly, or register for daily alerts for your regular commute.
3. **Daily Checks**: If you register for updates, you set a time (e.g., 7 AM for jogging). The app checks if your usual path is polluted today. If it's bad, it suggests a cleaner route.
4. **Warnings**: If pollution is too high, the app warns the user before they leave.
5. **Route Finding**: The backend logic needs to fetch a few different ways to get to the destination and pick the one with the lowest air pollution score.
6. **One-off trips**: Single trips work the same as the daily ones for checking air quality.
7. **Custom Allergies**: Users can add specific allergies (like PM2.5 or Ozone). The app will try to prioritize routes avoiding those specific triggers.

## Non-Functional Requirements
1. **Data Freshness**: Air quality data needs to be pulled from the API frequently so it's accurate.
2. **Map Accuracy**: The routes and distances need to snap to real roads/streets accurately (going to use OpenRouteService for this).
3. **Reliability**: App shouldn't crash if the weather API goes down, need fallback handling.
4. **Speed**: Route calculation should take less than 3-4 seconds otherwise the UI feels laggy.
5. **Privacy**: Need to make sure location data is secure.
6. **Scalability**: The background worker that checks daily routes needs to be able to handle multiple users without crashing the app.
