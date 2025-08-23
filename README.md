# Live Video Streaming Solution

A complete live video streaming application that captures webcam feed and streams it to RTMP destinations with HLS playback support. Built with Spring Boot, FFmpeg, and NGINX RTMP module.

## What This Solution Does

This application lets you stream live video from your webcam to any RTMP-compatible destination. It includes a web-based player that can display the live stream and play back recorded content. The solution is designed to handle network interruptions gracefully and provides real-time monitoring of stream health.

## Key Features

- **Webcam Capture**: Works with any standard webcam device
- **RTMP Streaming**: Stream to local servers or cloud platforms
- **HLS Playback**: Web-based player for live and recorded content
- **Auto-retry**: Automatically recovers from network issues
- **Real-time Monitoring**: Track stream status and health
- **REST API**: Complete API for managing streams
- **Docker Support**: Easy deployment with containers

## How It Works

The application uses FFmpeg to capture video from your webcam and stream it to an NGINX RTMP server. The NGINX server converts the RTMP stream to HLS format, which can be played in web browsers. The Spring Boot application manages the streaming process and provides a web interface for control.

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web Browser   │    │  Spring Boot    │    │   NGINX RTMP    │
│   (HLS Player)  │◄──►│   Application   │◄──►│   Server        │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │     FFmpeg      │
                       │   (Webcam)      │
                       └─────────────────┘
```

## Prerequisites

Before running this application, you'll need:

- **Java 21** or higher
- **FFmpeg** installed and accessible from command line
- **Docker** and Docker Compose
- **A webcam** connected to your computer
- **Modern web browser** (Chrome, Firefox, Safari, Edge)

## Getting Started

### Step 1: Start the NGINX RTMP Server

First, start the NGINX RTMP server that will receive your streams:

```bash
# Start the server
docker-compose up -d

# Check if it's running
curl http://localhost:8082/health
```

The NGINX server runs on:
- RTMP port: 1935 (for receiving streams)
- HTTP port: 8082 (for serving HLS content)

### Step 2: Configure FFmpeg Path

Edit the application properties file to point to your FFmpeg installation:

```properties
# src/main/resources/application.properties
ffmpeg.path=C:\\path\\to\\your\\ffmpeg.exe
```

On Windows, the path might look like: `C:\\ffmpeg\\bin\\ffmpeg.exe`
On Linux/Mac: `/usr/bin/ffmpeg`

### Step 3: Run the Application

Build and start the Spring Boot application:

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/webcam-0.0.1-SNAPSHOT.war
```

The application will start on `http://localhost:8080`

### Step 4: Start Streaming

1. Open your browser and go to `http://localhost:8080`
2. Enter your webcam device name (e.g., "HD Webcam")
3. Enter the RTMP destination: `rtmp://localhost:1935/hls/test3`
4. Click "Start Stream"

The web interface will automatically load and play the stream once it starts.

## Using the Application

### Starting a Stream

**Through the Web Interface:**
1. Navigate to `http://localhost:8080`
2. Enter your webcam device name
3. Enter the RTMP destination URL
4. Click "Start Stream"

**Using the API:**
```bash
curl -X POST http://localhost:8080/api/stream/create \
  -H "Content-Type: application/json" \
  -d '{
    "inputStream": "HD Webcam",
    "destination": "rtmp://localhost:1935/hls/test3"
  }'
```

### Playing the Stream

Once a stream is started, you can play it in several ways:

1. **Web Interface**: The player automatically loads the stream
2. **Direct URL**: `http://localhost:8082/hls/test3/index.m3u8`
3. **VLC Media Player**: Open the HLS URL directly
4. **Other HLS Players**: Any player that supports HLS format

### Monitoring and Control

**Check Stream Status:**
```bash
curl http://localhost:8080/api/stream/{streamId}/status
```

**Stop a Stream:**
```bash
curl -X DELETE http://localhost:8080/api/stream/{streamId}
```

**Web Interface**: Use the "Stop Stream" button in the web interface

## API Reference

### Create Stream
```http
POST /api/stream/create
Content-Type: application/json

{
  "inputStream": "HD Webcam",
  "destination": "rtmp://localhost:1935/hls/test3"
}
```

**Response:**
```json
{
  "streamId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Stream created successfully",
  "success": true
}
```

### Get Stream Status
```http
GET /api/stream/{streamId}/status
```

**Response:**
```json
{
  "streamId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "startTime": "2024-01-15T10:30:00",
  "retryCount": 0,
  "isActive": true
}
```

### Stop Stream
```http
DELETE /api/stream/{streamId}
```

## Configuration

### Application Settings

The main configuration is in `src/main/resources/application.properties`:

```properties
# FFmpeg executable path
ffmpeg.path=C:\\path\\to\\ffmpeg.exe

# Server port
server.port=8080

# Logging level
logging.level.com.video.stream.webcam=DEBUG
```

### NGINX RTMP Configuration

The NGINX server is configured with these settings:
- **RTMP Port**: 1935
- **HTTP Port**: 8082
- **HLS Fragment Duration**: 3 seconds
- **HLS Playlist Length**: 60 seconds
- **CORS**: Enabled for web player compatibility

## Troubleshooting

### Common Issues and Solutions

**1. FFmpeg Not Found**
```
Error: FFmpeg path is not configured
```
**Solution**: Update the `ffmpeg.path` property in `application.properties`

**2. Webcam Not Available**
```
Error: Webcam 'HD Webcam' is not available
```
**Solution**: 
- Check your webcam device name: `ffmpeg -f dshow -list_devices true -i dummy`
- Make sure the webcam is not being used by another application

**3. RTMP Connection Failed**
```
Error: Failed to connect to RTMP server
```
**Solution**: 
- Ensure NGINX RTMP server is running: `docker-compose ps`
- Check if port 1935 is available

**4. HLS Playback Issues**
```
Error: HLS manifest not found
```
**Solution**: 
- Wait for the stream to start (may take a few seconds)
- Check NGINX logs: `docker-compose logs nginx-rtmp`

### Debug Commands

**Check FFmpeg Installation:**
```bash
ffmpeg -version
```

**List Available Webcam Devices:**
```bash
ffmpeg -f dshow -list_devices true -i dummy
```

**Test Webcam Capture:**
```bash
ffmpeg -f dshow -i video="HD Webcam" -t 5 test.mp4
```

**Check NGINX Logs:**
```bash
docker-compose logs nginx-rtmp
```

**Test RTMP Connection:**
```bash
ffmpeg -i test.mp4 -c copy -f flv rtmp://localhost:1935/hls/test
```

## Development

### Project Structure
```
src/
├── main/
│   ├── java/
│   │   └── com/video/stream/webcam/
│   │       ├── controller/     # REST API endpoints
│   │       ├── service/        # Business logic
│   │       ├── dto/           # Data transfer objects
│   │       └── config/        # Configuration classes
│   └── resources/
│       ├── static/            # Web interface files
│       └── application.properties
└── test/                      # Unit tests
```

### Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd webcam

# Build the project
mvn clean package

# Run tests
mvn test

# Run the application
mvn spring-boot:run
```

### Running Tests

The project includes comprehensive unit tests for the service layer:

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=StreamServiceTest

# Run with Windows batch script
run-tests.bat
```

## Deployment

### Docker Deployment

**Build and Run with Docker Compose:**
```bash
# Build the application
mvn clean package

# Start all services
docker-compose up -d
```

**Docker Compose Services:**
- **webcam-app**: Spring Boot application (port 8080)
- **nginx-rtmp**: NGINX RTMP server (ports 1935, 8082)

### Production Considerations

For production deployment, consider:

1. **Security**: Add authentication and authorization
2. **Monitoring**: Implement health checks and metrics
3. **Scaling**: Use load balancers for multiple instances
4. **Storage**: Configure persistent storage for HLS segments
5. **SSL**: Enable HTTPS for web interface and API

## Performance and Optimization

### FFmpeg Settings

The application uses optimized FFmpeg settings for low-latency streaming:

```bash
ffmpeg -f dshow -rtbufsize 50M -i video="HD Webcam" \
  -vcodec libx264 -preset ultrafast -tune zerolatency \
  -crf 28 -maxrate 2000k -bufsize 4000k \
  -pix_fmt yuv420p -g 30 -r 30 \
  -acodec aac -b:a 128k -ar 44100 \
  -f flv rtmp://destination
```

### NGINX Optimization

- **HLS Fragment Duration**: 3 seconds for low latency
- **Playlist Length**: 60 seconds for buffer management
- **CORS Headers**: Enabled for web player compatibility

## Future Enhancements

The current implementation provides a solid foundation for live video streaming. Here are some potential improvements that could be added:

### **Multi-Stream Support**
- Handle multiple concurrent webcam streams
- Stream management dashboard
- Load balancing for multiple streams

### **Recording and Playback**
- Automatic stream recording
- Video file management
- Playback controls and timeline

### **Advanced Analytics**
- Stream quality metrics
- Viewer statistics
- Performance monitoring

### **Cloud Integration**
- AWS Kinesis Video Streams support
- Cloud storage for recordings
- CDN integration for global distribution

### **AI and Computer Vision**
- Real-time object detection
- Face recognition
- Motion detection alerts

### **Security Enhancements**
- User authentication and authorization
- Stream encryption
- Access control and permissions

### **Mobile Support**
- Mobile-optimized web interface
- Native mobile applications
- Push notifications for stream events

## Technical Architecture

### **Scalability Design**

The application is designed with scalability in mind:

1. **Microservice Architecture**: Spring Boot services can be deployed independently
2. **Containerization**: Docker support for easy deployment and scaling
3. **Stateless Design**: Services don't maintain state, allowing horizontal scaling
4. **External Dependencies**: FFmpeg and NGINX can be scaled separately

### **Reliability Features**

1. **Auto-retry Mechanism**: Automatically retries failed streams
2. **Status Monitoring**: Real-time stream health tracking
3. **Error Handling**: Comprehensive error handling and logging
4. **Graceful Degradation**: Continues operation even with partial failures

### **Network Outage Handling**

1. **Connection Monitoring**: Detects network interruptions
2. **Automatic Recovery**: Restarts streams when connection is restored
3. **Status Persistence**: Maintains stream state across restarts
4. **Health Checks**: Regular monitoring of stream health
