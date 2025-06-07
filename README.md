🎬 StreamFlow
-
A robust full-stack video streaming application built with Spring Boot and React.js. StreamFlow allows users to upload, process, and stream video content seamlessly. It supports adaptive bitrate streaming (HLS) with ffmpeg for transcoding, ensuring optimal viewing experiences across various devices and network conditions.

🚀 Features
----
StreamFlow comes packed with a comprehensive set of features designed for a complete video streaming experience:

Video Upload & Asynchronous Processing:
-

Users can upload video files with titles and descriptions via a dedicated modal.

Asynchronous processing is initiated via Apache Kafka messaging, featuring an idempotency check to prevent duplicate processing.

Videos are processed using ffmpeg by a dedicated Video Processing Microservice to create multiple quality renditions (adaptive bitrate HLS) and thumbnails.

Includes robust error handling and email notifications for processing status (success/failure).

The original raw video file is automatically cleaned up after successful processing.

Features an upload progress bar.

Adaptive Bitrate Streaming (HLS):
-

Implements HLS (.m3u8 playlists and .ts segments) for smooth, adaptive video playback.

The FFmpegService intelligently transcodes videos into multiple predefined resolutions (e.g., 240p, 360p, 480p, 720p, 1080p), generating a master playlist for adaptive streaming.

Video streaming endpoints are secured using a dedicated HLS token validation filter that extracts and validates tokens from query parameters, ensuring secure content delivery.

Video Management:
-

Users can view, edit metadata (title, description), and delete their own uploaded videos via the "My Videos" dashboard.

Concurrent updates to video data (like view counts or status changes) are managed using JPA Pessimistic Locking to ensure data consistency.

Comprehensive file and directory deletion is handled when a video is removed, including the original raw file and all processed HLS renditions.

Public Dashboard:
-

A central dashboard allows all users to browse and stream available videos.

Displays detailed video information including name, description, file size, current processing status, duration, views, and the uploader's username.

Video View Counter: Tracks and displays the number of views for each video.

User Authentication & Authorization:
-

Secure user registration and login with JWT-based authentication.

Users can log in using either their username or email.

The frontend implements an automatic token refresh mechanism to maintain user sessions seamlessly (via axiosInstance interceptors).

OTP Verification: Email-based OTP is used during user registration for enhanced security. OTPs are hashed using SHA-256 and managed in Redis with cooldowns and attempt limits.

Password Management: Includes functionality for forgot password and reset password flows, allowing users to request and reset their password via email links. Password reset tokens are generated as UUIDs, stored in Redis with an expiration, and invalidated after use.

User Profile Management: Dedicated user profile page for managing personal information like username, email, and account creation date.

Logout & Token Blacklisting using Redis.

Robust Error Handling:
-

Comprehensive global exception handling (@ControllerAdvice) catches various application-specific exceptions (e.g., UserAlreadyExistsException, InvalidOtpException, TokenNotFoundException, VideoRetrievalException, ResourceNotFoundException) and maps them to appropriate HTTP status codes, providing clear error messages.

Kafka consumer error handling includes retries and logging.

Frontend uses react-toastify for user-friendly notifications.
-

Data Validation: Utilizes Jakarta Bean Validation (@Valid, @NotBlank, @Size, @Email, @Pattern) for ensuring data integrity on incoming API requests.

Modular File Storage: A flexible file storage service manages video files and thumbnails on the local filesystem, with support for user-specific and type-specific subdirectories. It offers robust operations for storing, loading, resolving paths, creating/deleting directories, and deleting individual files.

Modular Architecture: Designed with modularity in mind, allowing for easy integration of different backend services and future cloud storage solutions.

Rich React Frontend: Features protected routes, toast notifications for user feedback, and comprehensive authentication context management, ensuring a clear separation of UI components and pages.

🛠️ Tech Stack
---

Layer | Technology
---|---
|Backend | Main Application: Spring Boot 3.x, Spring WebFlux (for streaming), Spring Data JPA, Lombok, Logback. 
|Video Processing Microservice| Spring Boot 3.x, ffmpeg integration (Bytedeco JavaCV), Apache Kafka Consumer, Spring Data JPA (shared entities/repos), Logback.|
|Auth | Spring Security, JWT (jjwt), Redis (for token blacklisting, OTP flags, and password reset tokens), Jakarta Bean Validation|
|Data Store | MySQL|
|Video Storage | Local File System (Scalable to Cloud Storage in future)|
|Email | Jakarta Mail (SMTP) for OTP, password reset/confirmation emails, and video processing status notifications.|
|Build Tool | Maven|
|Java | Java 17+|
|Frontend | React.js|
|Routing | react-router-dom|
|Notification | react-toastify|
|Video Player | Video.js, HLS.js (for HLS playback)|



📦 Prerequisites
-
Before running StreamFlow, ensure you have the following installed and configured:

Java JDK 17+

Maven 3.8+

MySQL (local or Docker)

ffmpeg: Essential for video transcoding and thumbnail generation. Ensure it's in your system's PATH.

Installation Guides: ffmpeg.org

Docker (for Redis and/or Kafka, if running locally)

Redis Setup with Docker:

docker run --name redis-streamflow -p 6379:6379 -d redis

-------------

Kafka Setup with Docker:

For example, using a simple docker-compose.yml file:


version: '3'

services:

  zookeeper:
  
    image: confluentinc/cp-zookeeper:7.0.1
    
    hostname: zookeeper
    
    container_name: zookeeper
    
    ports:
    
      - "2181:2181"
      
    environment:
    
      ZOOKEEPER_CLIENT_PORT: 2181
      
      ZOOKEEPER_TICK_TIME: 2000
      
  kafka:
  
    image: confluentinc/cp-kafka:7.0.1
    
    hostname: kafka
    
    container_name: kafka
    
    ports:
    
      - "9092:9092"
      
    environment:
    
      KAFKA_BROKER_ID: 1
      
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:29092
      
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      
    depends_on:
      - zookeeper
      

Run with: docker-compose up -d


------------------------------------
Node.js & npm (for frontend)


⚙️ Core Configuration
-
Update your application.properties (or application.yml) with the following details for both the main streaming application and the video processing microservice:

Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/streamflow_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=your-mysql-user
spring.datasource.password=your-mysql-password
spring.jpa.hibernate.ddl-auto=update # or create
spring.jpa.show-sql=true

JWT & HLS JWT Secret Configuration (Main Application Only)
Define the secrets for general JWTs and for the specific HLS streaming tokens. These should be strong, randomly generated strings.

jwt.secret=your_super_secret_jwt_key_that_is_at_least_256_bits_long
hls.jwt.secret=your_separate_secret_key_for_hls_tokens_at_least_256_bits

Video Storage Configuration (Both Applications)
Specify the base directory for storing uploaded videos, transcoded files, and thumbnails. This should be a path accessible by your Spring Boot application.

files.video.base-path=/path/to/your/video/storage

Note: For production, consider using a dedicated network attached storage (NAS), cloud storage, or a more robust file management solution.

Redis Configuration (Both Applications)
Configure Redis connection details, primarily for token blacklisting, OTP verification flags, and password reset tokens.

spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password= # Leave empty if no password is set for Redis

Kafka Configuration (Both Applications)
Specify the bootstrap servers for your Kafka cluster, used for message queuing of video processing requests. The Video Processing Microservice will also require a group-id for its consumer.

 For both applications
spring.kafka.bootstrap-servers=localhost:9092 # Adjust if your Kafka broker is elsewhere

 For Video Processing Microservice specifically
spring.kafka.consumer.group-id=video-processor-group
 Ensures the consumer processes only one message at a time, suitable for long-running tasks.
spring.kafka.consumer.properties.max.poll.records=1
// Increased to 50 minutes (3,000,000 ms) to accommodate long video processing times without group rebalancing.
spring.kafka.consumer.properties.max.poll.interval.ms=3000000

CORS Configuration (Main Application Only)
-
If your frontend and backend are on different domains/ports during development (e.g., React on 3000, Spring Boot on 8080), you'll need CORS configured:

cors.allowed.origins=http://localhost:3000
cors.allowed.methods=GET,POST,PUT,DELETE,PATCH
cors.allowed.headers=*
cors.allowed.credentials=true

Frontend Integration Properties (Main Application Only)
-
Specify the base URL for your frontend's password reset page. This is used by the backend to construct the password reset email link.

app.frontend.password-reset-url=http://localhost:3000/reset-password

Email Configuration (Video Processing Microservice Only)
The Video Processing Microservice requires mail sender configuration for sending status notifications.

spring.mail.host=your.smtp.host
spring.mail.port=587 # or your SMTP port
spring.mail.username=your-smtp-username
spring.mail.password=your-smtp-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

🧱 Module Structure
----
StreamFlow is designed with a modular, multi-service backend architecture comprising two main Spring Boot applications: the Main Streaming Application and the Video Processing Microservice.

Backend - Main Streaming Application (com.robspecs.streaming)
---
This application primarily handles user authentication, video metadata management, and serving processed video streams.

⦁	config: Application-wide configurations (e.g., security, file storage, Redis, Kafka, CORS).
⦁	controller: REST endpoints for video management, user authentication, and streaming.

⦁	service: Business logic for user operations, data manipulation, including AuthService, UserService, OtpService, MailService, TokenBlacklistService, PasswordResetTokenService, and FileStorageService.

⦁	repository: Data access layer for interacting with MySQL, including UserRepository and VideosRepository, with support for custom queries and Pessimistic Locking for Video entities to ensure data consistency during updates. Shared with the Video Processing Microservice.

⦁	entity: JPA entities representing database tables, such as User (implementing UserDetails for Spring Security integration) and Video (with fields for videoName, originalFilePath, description, fileSize, status, durationMillis, resolutionFilePaths, uploadUser, views, and thumbnailData). Shared with the Video Processing Microservice.

⦁	security: Custom JWT authentication filters (JWTAuthenticationFilter for login, JWTValidationFilter for access token validation, JWTRefreshFilter for explicit token refresh), Spring Security configurations (SecurityConfig), custom authentication entry point (JWTAuthenticationEntryPoint), and the HlsTokenValidationFilter for stream security.

⦁	utils: Helper classes including JWTUtils for JWT token generation and validation (both regular and HLS-specific tokens).

⦁	dto: Data Transfer Objects for request/response payloads (e.g., RegistrationDTO, LoginDTO, ForgotPasswordRequest, ResetPasswordRequest, VideoUploadDTO, VideoUpdateRequest, VideoDetailsDTO, UserDTO, UserProfileDTO).

⦁	enums: Enumerations for statuses and roles (VideoStatus, Roles).

⦁	exceptions: Custom exception classes for specific error scenarios.

⦁	exceptionhandler: Global exception handler (GlobalExceptionHandler).

Backend - Video Processing Microservice (com.robspecs.videoprocessor)
---
This is a separate Spring Boot application responsible solely for consuming video processing requests from Kafka, performing transcoding, and updating video metadata.

VideoProcessorServiceApplication: The main application class for this microservice, configured to scan components, JPA repositories, and entities from both its own package and the shared com.robspecs.streaming package.

⦁	config:

⦁	KafkaConsumerConfig: Configures Kafka consumer properties and integrates a custom error handler for robust message consumption.

⦁	KafkaErrorHandlerConfig: Defines a DefaultErrorHandler for Kafka listeners, specifying retry logic (e.g., 2 retries with a 5-second fixed backoff) before logging failed processing.
	
⦁	AsyncConfig: Configures ThreadPoolTaskExecutor beans (taskExecutor for general async tasks like emails and videoProcessingExecutor specifically for video processing tasks) to enable asynchronous method execution.

⦁	dto:-
  VideoProcessingRequest: A Serializable DTO used for messages sent via Kafka, containing videoId, originalFilePath, fileSize, uploadUserEmailOrUsername, and uploadUserId.

	VideoMetadata: A Serializable DTO used internally to encapsulate video information (duration, dimensions, codecs, bitrate) extracted by ffmpeg.

⦁	service:
	
 VideoProcessorService: The core service that listens for Kafka messages, immediately submitting the complex processing to a dedicated asynchronous thread pool (videoProcessingExecutor). It includes:

 Idempotency Check: Skips processing if a video is already marked READY, handling potential duplicate Kafka messages.
	
 Workflow Orchestration: Manages the sequence of operations: fetching video metadata, generating a thumbnail, performing multi-resolution HLS transcoding, updating the video's status      (PROCESSING -> READY or FAILED), and sending email notifications.

 Raw File Cleanup: Deletes the original uploaded video file from storage after successful processing.
	
 FFmpegService: This crucial service within the microservice handles all ffmpeg interactions using Bytedeco JavaCV. It provides:
	
 getMediaInfo: Extracts detailed metadata (duration, resolution, codecs, bitrate) from the original video.
	
 transcodeToHLS: Transcodes the video into multiple HLS resolution profiles (e.g., 240p, 360p, 480p, 720p, 1080p). It generates individual .m3u8 playlists and .ts segments for each        resolution, and then a master .m3u8 playlist that references all available renditions for adaptive streaming.
 
 generateThumbnail: Extracts a frame from the video at a specified timestamp and generates a JPEG thumbnail.

 FileStorageService: A dedicated service for managing all video-related files on the local filesystem. It includes methods for storeFile, loadFileAsResource,                                resolvePath,createDirectory, deleteDirectory, deleteFile, getProcessedVideoDirectory, and copyFile.

 EmailService: An @Async service for sending email notifications to users regarding the success or failure of their video processing tasks.

Frontend (React.js)
--
The React frontend is built to provide an intuitive and responsive user experience for video streaming.

⦁	AuthContext.js (AuthContext and AuthProvider): A central React Context for managing the global authentication state.
	
⦁	Maintains isAuthenticated (boolean), accessToken (string), and loadingAuth (boolean) states.
	
⦁	On initial load, it attempts to validate any stored token via /auth/validate to determine authentication status.
	
⦁	Provides login and logout functions to update authentication state and interact with localStorage for token persistence.
	
⦁	Registers an updateToken callback with axiosInstance's interceptors, allowing automatic token refresh to update global accessToken state.
	
⦁	axiosInstance.js: A centralized Axios instance configured with the API base URL and withCredentials for cookie handling. Includes powerful interceptors for:

⦁	Request Interceptor: Automatically attaches the user's accessToken from localStorage to all outgoing requests.

⦁	Response Interceptor: Implements robust automatic token refresh logic. If a 401 Unauthorized error occurs (indicating an expired access token), it triggers a call to /auth/refresh,  queues subsequent API requests, and retries them with the new token upon successful refresh. If refresh fails, it logs out the user and redirects to the login page.

⦁	config.js: Defines the API_BASE_URL for the backend, centralizing API endpoint configuration.

⦁	Footer.jsx: A simple presentational component for the application's footer.

⦁	Header.jsx: The main navigation bar, dynamically displaying "MyTube" and a page-specific title. Provides navigation links and buttons for login/register/upload/logout.

⦁LoadingSpinner.jsx: A reusable component to display a loading animation.

⦁	Modal.jsx: A generic, reusable modal component for displaying overlays.

⦁	ProtectedRoute.jsx: A React Router wrapper component that ensures only authenticated users can access specific routes.

⦁	Sidebar.jsx: Provides left-hand navigation for authenticated users (Discover, My Videos, Profile).
	
⦁	VideoCard.jsx: A component responsible for displaying individual video details on dashboards and user's video lists.

⦁	UploadVideoModal.jsx: A modal form for users to upload new videos, with progress bar and client-side validation. Uses react-toastify for notifications.
	
⦁	Dashboard.jsx: The main public video feed page, fetching and displaying all available videos.

⦁	ForgotPasswordPage.jsx: Allows users to initiate the password reset process.

⦁	Login.jsx: Handles user login with enhanced error handling and "Forgot Password?" link.
	
⦁	MyVideosPage.jsx: Displays a list of videos uploaded by the currently authenticated user, with edit and delete functionality.

⦁	ProfilePage.jsx: Displays the authenticated user's profile information.

⦁	VideoPlayerPage.jsx: Manages the video playback using Video.js and HLS.js, including dynamic URL fetching and quality level selection.

🎬 Video Processing & Streaming Flow
--

Video Upload:

Client Upload: User uploads a video via UploadVideoModal. Form data is sent to the Main Streaming Application.

Backend Storage: The Main Streaming Application stores the raw video file on the local file system.

Kafka Message Publication: A message with video details is published to a Kafka topic (video-upload-events) to trigger asynchronous processing.

Asynchronous Video Processing (Dedicated Microservice):

Kafka Consumer: The Video Processing Microservice consumes VideoProcessingRequest messages from Kafka and immediately submits the task to an asynchronous thread pool.

Idempotency & Status Update: Checks if the video is already READY (idempotency) and updates the status to PROCESSING. Includes robust error handling with retry logic.

FFmpeg Processing: The FFmpegService extracts media info, performs multi-resolution HLS transcoding (generating .ts segments and .m3u8 playlists, including a master playlist), and generates a thumbnail.

Database Update & Cleanup: Upon successful completion, the Video entity's status is updated to READY, thumbnail data and HLS playlist paths are recorded, and the original raw video file is deleted.

Email Notification: An EmailService sends email notifications to the user about processing status.

Video Streaming (HLS):

Client Request: When a user selects a video, the React frontend's Video.js player requests the main .m3u8 playlist from the Main Streaming Application (e.g., /api/videos/stream/{videoId}/master.m3u8?token=...).

HLS Token Validation: The request is intercepted by the HlsTokenValidationFilter, which validates the HLS token (generated by JWTUtils.generateHlsToken) from the query parameters, ensuring authorized access.

HLS Segment Delivery: Once validated, the backend serves the .m3u8 playlist and subsequent .ts segments. The Video.js player (with hls.js) adaptively requests segments based on network conditions.

View Count Update: The backend increments the video's view counter, using JPA Pessimistic Locking for accurate concurrent updates.

🔐 Authentication Flow
--
Register: User registers via /api/auth/register after successful OTP verification. The RegistrationDTO is validated, and the account is enabled.

OTP Verification:

User requests an OTP to their email via /api/auth/otp/request. An OTP is generated (hashed and stored in Redis) and emailed. A cooldown is enforced.

User verifies OTP by sending email and OTP to /api/auth/otp/verify. OtpServiceImpl validates, tracks failed attempts, and sets a temporary verified flag in Redis on success.

Login:

User submits username/email and password to /api/auth/login.

JWTAuthenticationFilter intercepts, validates LoginDTO, and authenticates credentials.

On successful authentication, a short-lived Access Token (15 min expiration) is placed in the Authorization header, and a longer-lived Refresh Token (7 days expiration) is set as an HttpOnly cookie.

The frontend's AuthContext stores the token and updates global state, navigating to the Dashboard.

Token Validation & Refresh:

For subsequent protected API requests, the Access Token is validated by JWTValidationFilter.

If expired/invalid, the frontend's axiosInstance interceptor automatically attempts to refresh the token using the refresh token (sent as an HttpOnly cookie to /api/auth/refresh).

Subsequent requests are queued until refresh is complete, then retried with the new token. If refresh fails, the user is prompted to log in again.

Logout: Invalidates both Access and Refresh tokens by blacklisting them in Redis via /api/auth/logout. Clears Spring Security context and expires the refresh token cookie. Frontend's AuthContext clears client-side state.

Forgot Password: User requests password reset via /api/auth/forgot-password. AuthService generates a unique reset token (stored in Redis), sends a reset link via email, and returns a generic success message for security.

Reset Password: User provides new password and reset token to /api/auth/reset-password. AuthService validates the token, updates the password (BCryptPasswordEncoder), invalidates the token in Redis, and sends a confirmation email.

🚀 Production-Ready Practices (Optional Enhancements)
-----
These enhancements are planned for future development to make StreamFlow even more robust and scalable:

Add Swagger/OpenAPI documentation for REST APIs to make development and API consumption easier.

Implement advanced search and filtering for videos on the dashboard.

Add user playlists or "watch later" functionality.

Implement a commenting or rating system for videos.

Improve logging levels and externalize log configuration for production.

Consider a separate microservice for authentication if the application scales significantly.

HTTPS Support: Essential for securing all data in transit during deployment. Crucial to set secure(true) for HTTP-only cookies in production.

Secrets Management: Externalize sensitive credentials (DB passwords, JWT secrets, email credentials) using environment variables or a secrets manager (e.g., HashiCorp Vault, AWS Secrets Manager).

Cloud Storage Integration: Integrate with services like AWS S3, Google Cloud Storage, or Azure Blob Storage for scalable, durable, and cost-effective video storage.

Load Balancing & Scalability: Implement load balancing and containerization (Docker, Kubernetes) for handling high traffic and scaling different services independently.

Monitoring & Logging: Utilize tools like Prometheus/Grafana for application monitoring and implement structured logging (SLF4J + Logback) for better diagnostics and centralized log management.

CDN Integration: For optimized video delivery and reduced server load, integrate with a Content Delivery Network (CDN) to serve HLS segments geographically closer to users.

Transaction Management: Ensure robust @Transactional annotations are correctly applied across services for all complex database operations to maintain data consistency and integrity.

Frontend Error Handling: Enhance frontend to gracefully handle and display error messages from the backend's global exception handler.

🧑‍💻 Author
--
Ankit Pandey

LinkedIn (https://www.linkedin.com/in/ankitpandeyap/)

GitHub (https://github.com/ankitpandeyap)
