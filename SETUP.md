# Setup Instructions for VS Code

## Step 1: Install Prerequisites

### Java 17
```bash
# On Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# On macOS
brew install openjdk@17

# On Windows
# Download from https://adoptium.net/
```

### Maven
```bash
# On Ubuntu/Debian
sudo apt install maven

# On macOS
brew install maven

# On Windows
# Download from https://maven.apache.org/download.cgi
```

Verify installations:
```bash
java -version   # Should show Java 17+
mvn -version    # Should show Maven 3.6+
```

## Step 2: Setup VS Code

1. **Install Visual Studio Code**: Download from https://code.visualstudio.com/

2. **Install Required Extensions**:
   - Open VS Code
   - Press `Ctrl+Shift+X` (Windows/Linux) or `Cmd+Shift+X` (Mac)
   - Search and install:
     - "Extension Pack for Java" by Microsoft
     - "Maven for Java" by Microsoft
     - "Debugger for Java" by Microsoft

## Step 3: Open Project in VS Code

1. Open VS Code
2. Click `File > Open Folder`
3. Navigate to the `raft-cache` directory
4. Click "Select Folder"

VS Code will automatically detect it's a Maven project and start indexing.

## Step 4: Build the Project

### Option A: Using VS Code Terminal
1. Open terminal in VS Code: `` Ctrl+` `` (backtick)
2. Run:
   ```bash
   mvn clean install
   ```

### Option B: Using Maven Commands View
1. Click on "Explorer" icon in left sidebar
2. Find "MAVEN" section at bottom
3. Expand your project
4. Right-click on "Lifecycle" → "clean"
5. Right-click on "Lifecycle" → "install"

You should see:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

## Step 5: Run the Application

### Method 1: Command Line
```bash
# In VS Code terminal
mvn exec:java -Dexec.mainClass="com.distributed.cache.Main" -Dexec.args="node1 8001"
```

### Method 2: Debug Configuration
1. Click on "Run and Debug" icon in left sidebar (or press `Ctrl+Shift+D`)
2. Click "create a launch.json file"
3. Select "Java"
4. Replace contents with:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Run Node 1",
            "request": "launch",
            "mainClass": "com.distributed.cache.Main",
            "projectName": "raft-cache",
            "args": "node1 8001"
        },
        {
            "type": "java",
            "name": "Run Node 2",
            "request": "launch",
            "mainClass": "com.distributed.cache.Main",
            "projectName": "raft-cache",
            "args": "node2 8002"
        },
        {
            "type": "java",
            "name": "Run Node 3",
            "request": "launch",
            "mainClass": "com.distributed.cache.Main",
            "projectName": "raft-cache",
            "args": "node3 8003"
        }
    ]
}
```

5. Press F5 to start debugging

## Step 6: Run Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=CacheStoreTest

# Run with coverage (install jacoco plugin)
mvn clean test jacoco:report
```

## Troubleshooting

### Issue: "Java not found"
- Make sure Java 17+ is installed and in PATH
- Restart VS Code after installing Java

### Issue: "Cannot resolve imports"
- Press `Ctrl+Shift+P` and type "Java: Clean Java Language Server Workspace"
- Select the command and restart VS Code

### Issue: Maven dependencies not downloading
- Check internet connection
- Try: `mvn dependency:purge-local-repository`
- Delete `~/.m2/repository` and rebuild

### Issue: Port already in use
- Change the port number in the run arguments
- Or kill the process using the port:
  ```bash
  # Linux/Mac
  lsof -ti:8001 | xargs kill -9
  
  # Windows
  netstat -ano | findstr :8001
  taskkill /PID <PID> /F
  ```

## Next Steps

1. Read the README.md for project overview
2. Start implementing network layer (week 1 task)
3. Follow the development roadmap in README.md
4. Run tests frequently: `mvn test`

## Useful VS Code Shortcuts

- `Ctrl+Shift+P`: Command palette
- `Ctrl+P`: Quick file open
- `Ctrl+Space`: Auto-complete
- `F12`: Go to definition
- `Shift+F12`: Find all references
- `Ctrl+Shift+F`: Search in files
- `` Ctrl+` ``: Toggle terminal

## Git Setup (Optional but Recommended)

```bash
cd raft-cache
git init
git add .
git commit -m "Initial project setup"
git branch -M main
git remote add origin <your-github-repo-url>
git push -u origin main
```

## Questions?

Refer to:
- Project README.md
- Maven documentation: https://maven.apache.org/guides/
- Raft paper: https://raft.github.io/raft.pdf
