# Import - Simple & Lightweight Task App

A simple Android app for managing tasks, built with Jetpack Compose and Firebase. Supports cloud sync and offline use.

## Features

### Task Management
- **Add, Edit, Delete Tasks**: Manage your tasks easily
- **Priority Levels**: 5 levels with colors:
    - 1 (Blue) - Low
    - 2 (Green) - Normal
    - 3 (Yellow) - Medium
    - 4 (Red) - High
    - 5 (Purple) - Critical
- **Due Dates**: Set deadlines for tasks
- **Complete Tasks**: Mark tasks as done or not done
- **Completed Tasks View**: See finished tasks and their count

### Authentication & Sync
- **Firebase Login**: Sign up and log in securely
- **Email Check**: Verify your email for security
- **Password Reset**: Reset password by email
- **Cloud Sync**: Sync tasks across devices
- **Offline Use**: Works without internet

### Data Management
- **JSON Import/Export**: Backup and restore tasks
- **Local Storage**: Saves data on your device
- **Live Updates**: Syncs instantly when logged in
- **Batch Actions**: Clear finished or all tasks

## Tech Stack

### Main Libraries
- **Firebase**: Login and database
- **Jetpack Compose**: UI toolkit
- **DataStore**: Local storage
- **Lifecycle**: ViewModel and state
- **Gson**: Handles JSON
- **Material 3**: UI components

## Getting Started

### Requirements
- Android Studio 2025.x or newer
- JDK 11+
- Android SDK API 36+
- Firebase project

### Build Steps

1. **Clone the repo**
     ```bash
     git clone https://github.com/g4mless/import
     cd import
     ```

2. **Set up Firebase**
     - Make a Firebase project at [Firebase Console](https://console.firebase.google.com)
     - Turn on Email/Password login
     - Turn on Firestore
     - Download `google-services.json` and put it in `app/`

3. **Build and Run**
     Open in Android Studio and run.

### Firebase Setup

You need Firebase Authentication and Firestore. Make sure:

- **Authentication**: Email/Password login is on
- **Firestore**: Database with this structure:
```js
rules_version = '2';

service cloud.firestore {
        match /databases/{database}/documents {

                match /users/{userId} {
                        allow create: if request.auth != null && request.auth.uid == userId;

                        allow read: if request.auth != null && request.auth.uid == userId;

                        allow update, delete: if false;

                        match /tasks/{taskId} {
                                allow read, write: if request.auth != null
                                                    && request.auth.uid == userId
                                                    && request.auth.token.email_verified == true;
                        }
                }
        }
}
```

## Usage

### Quick Start
1. **Add Tasks**: Tap the + button to create tasks
2. **Set Priority**: Pick a level (1-5) with color
3. **Due Dates**: Optionally set a deadline
```
You can use the app without an account, or create one if you want.
```

### Task Actions
- **View Tasks**: Main screen shows tasks by priority
- **Complete Tasks**: Tap checkbox to finish tasks
- **Edit Tasks**: Tap edit to change details
- **Delete Tasks**: Tap delete and confirm
- **View Completed**: Tap check icon for finished tasks

### Data Actions
- **Settings**: Tap settings icon
- **Export**: Save tasks as JSON
- **Import**: Load tasks from JSON
- **Clear Data**: Remove finished or all tasks
- **Account**: Sign out or manage login

## License

MIT License - see [LICENSE](LICENSE) for details.
