# ClearWay - MVP Requirements

## What the app needs to do (Functional)
1. **User Login**: People should be able to create an account and log in.
2. **Usage**: You can use the app to check random routes on the fly, or sign up for daily alerts for your regular commute.
3. **Daily Checks**: If you want daily updates, you just set a time (like for your morning jog). The app checks if your usual path is healthy today. If the air quality is bad, it finds a cleaner route for you to take instead.
4. **Bad Air Warnings**: If the pollution is way too high on your route, the app has to warn you before you head out.
5. **Route Finding Logic**: The backend needs to look at a few different ways to get to your destination (that are about the same distance) and pick the one with the least pollution.
6. **One-off Trips**: Checking a single trip works exactly the same as the daily ones—it checks the air and warns you if needed.
7. **Allergies & Custom Stuff**: Users can add specific allergies or pollutants they want to avoid (like PM2.5 or Ozone). The app will try to find a route that avoids those specific triggers.

## Under the Hood Requirements (Non-Functional)
1. **Up-to-date Data**: The air quality info needs to be pulled frequently from the API so it's actually accurate when someone is walking.
2. **Good Maps**: The routes and distances need to match real-world streets accurately.
3. **Uptime & Fallbacks**: Since this is health-related, the app shouldn't just crash if the weather API goes down; it needs to handle errors gracefully.
4. **Speed**: The app shouldn't leave users waiting around. Calculating those alternative routes should take less than 3 seconds.
5. **Privacy**: We're handling location data and health preferences, so user data needs to be locked down and secure.
6. **Handling Load**: The background worker that checks everyone's daily routes needs to be able to handle hundreds of users at once without slowing down the main website.
