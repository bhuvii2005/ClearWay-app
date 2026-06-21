# Requirements Document

## Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| **FR1** | **Login**: Users should be able to create an account and login securely. | Implemented |
| **FR2** | **Checking routes**: You can use the app to check random routes on the fly, or register for daily alerts for your regular commute. | Implemented |
| **FR3** | **Daily Checks**: If you register for updates, you set a time (e.g., 7 AM for jogging). The app checks if your usual path is polluted today. If it's bad, it suggests a cleaner route. | Partially Implemented (Scheduler exists, user-defined times pending) |
| **FR4** | **Warnings**: If pollution is too high, the app warns the user before they leave. | Partially Implemented (Logic exists, delivery mechanism pending) |
| **FR5** | **Route Finding**: The backend logic needs to fetch a few different ways to get to the destination and pick the one with the lowest air pollution score. | Implemented |
| **FR6** | **One-off trips**: Single trips work the same as the daily ones for checking air quality. | Implemented |
| **FR7** | **Custom Allergies**: Users can add specific allergies (like PM2.5 or Ozone). The app will try to prioritize routes avoiding those specific triggers. | Implemented |
| **FR8** | **Allergy Preference Management**: Users can update their pollutant sensitivities after registration. | Implemented |
| **FR9** | **Alternative Route Comparison**: System must present multiple routes ranked by pollution exposure. | Implemented |

## Non-Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| **NFR1** | **Data Freshness**: Air quality data needs to be pulled from the API frequently so it's accurate. | Implemented |
| **NFR2** | **Map Accuracy**: The routes and distances need to snap to real roads/streets accurately using the TomTom Routing API. | Implemented |
| **NFR3** | **Reliability**: App shouldn't crash if the weather or routing API goes down, need fallback handling. | Not Implemented |
| **NFR4** | **Speed**: Route calculation should take less than 3-4 seconds otherwise the UI feels laggy. | Implemented |
| **NFR5** | **Privacy**: Need to make sure location data is secure. | Partially Implemented (Passwords hashed, needs session/JWT auth) |
| **NFR6** | **Scalability**: The background worker that checks daily routes needs to be able to handle multiple users without crashing the app. | Implemented |
