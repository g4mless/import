# Import - Simple & Lightweight Task App

A simple Android app for managing tasks, built with Jetpack Compose.

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

### Data Management
- **JSON Import/Export**: Backup and restore tasks
- **Local Storage**: Saves data on your device
- **Batch Actions**: Clear finished or all tasks

## Tech Stack

### Main Libraries
- **Jetpack Compose**: UI toolkit
- **DataStore**: Local storage
- **Lifecycle**: ViewModel and state
- **Gson**: Handles JSON For Import & Export
- **Material 3**: UI components

## Getting Started

### Requirements
- Android Studio 2025.x or newer
- JDK 11+
- Android SDK API 36+

### Build Steps

1. **Clone the repo**
     ```bash
     git clone https://github.com/g4mless/import
     cd import
     ```

2. **Build and Run**
     Open in Android Studio and run.

## Usage

### Quick Start
1. **Add Tasks**: Tap the + button to create tasks
2. **Set Priority**: Pick a level (1-5) with color
3. **Due Dates**: Optionally set a deadline

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

## License

MIT License - see [LICENSE](LICENSE) for details.
