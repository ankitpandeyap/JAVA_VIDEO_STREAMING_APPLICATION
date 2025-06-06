import axios from "axios";
import { toast } from "react-toastify";
import { API_BASE_URL } from "../config/config";

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

let authUpdateToken = null;

export const setAuthUpdateToken = (callback) => {
  authUpdateToken = callback;
};

// Add a request interceptor to attach the token
axiosInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem("accessToken");
  // Check for token and that it's not the literal string "null"
  if (token && token !== "null") {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  // NEW LOGS HERE
  console.log(`[REQUEST] URL: ${config.url}, Token Present: ${!!token}, _retry: ${config._retry}`);
  return config;
});

// Define paths where automatic redirect to /login should be suppressed
const NO_REDIRECT_ON_REFRESH_FAILURE_PATHS = ["/login", "/register"];

let refreshTokenPromise = null;
let pendingRequestsQueue = [];

axiosInstance.interceptors.response.use(
  (response) => {
    console.log(`[RESPONSE SUCCESS] URL: ${response.config.url}, Status: ${response.status}`);
    return response;
  },
  async (error) => {
    const originalRequest = error.config;
    const status = error.response ? error.response.status : 'NO_STATUS';
    const url = originalRequest.url;

    // NEW LOGS HERE - IMPORTANT FOR DEBUGGING THE IF CONDITION
    console.error(`[RESPONSE ERROR] URL: ${url}, Status: ${status}`);
    console.error(`[RESPONSE ERROR] originalRequest._retry: ${originalRequest._retry}`);
    console.error(`[RESPONSE ERROR] Is 401: ${status === 401}`);
    console.error(`[RESPONSE ERROR] Not _retry: ${!originalRequest._retry}`);
    console.error(`[RESPONSE ERROR] Not /auth/login: ${url !== '/auth/login'}`);
    console.error(`[RESPONSE ERROR] Not /auth/refresh: ${url !== '/auth/refresh'}`);


    if (
      (status === 401 || status === 'NO_STATUS')&&
      !originalRequest._retry &&
      url !== '/auth/login' &&
      url !== '/auth/refresh'
    ) {
      console.log(`[RESPONSE ERROR] --- ALL REFRESH CONDITIONS MET FOR: ${url} ---`); // THIS SHOULD BE CALLED
      originalRequest._retry = true; // Mark this request as having attempted a retry

      if (!refreshTokenPromise) {
        console.log("[REFRESH] Initiating new refresh token request...");
        // Start refresh token request
        refreshTokenPromise = axiosInstance.post('/auth/refresh', {}, { withCredentials: true })
          .then((refreshApiResponse) => {
            console.log("[REFRESH] Refresh token successful.");
            const authorizationHeader = refreshApiResponse.headers['authorization'];

            if (authorizationHeader) {
              const tokenValue = authorizationHeader.startsWith('Bearer ')
                ? authorizationHeader.split(' ')[1]
                : authorizationHeader;

              localStorage.setItem('accessToken', tokenValue);
              axiosInstance.defaults.headers.common['Authorization'] = `Bearer ${tokenValue}`;
              if (authUpdateToken) {
                authUpdateToken(tokenValue);
              }

              // Retry all pending requests with new token
              console.log(`[REFRESH] Retrying ${pendingRequestsQueue.length} queued requests.`);
              pendingRequestsQueue.forEach((callback) => callback.resolve(tokenValue));
              pendingRequestsQueue = [];

              return tokenValue;
            } else {
              console.error('[REFRESH] No new token received from refresh endpoint.');
              toast.error('Session update failed (no new token received). Please log in again.');
              localStorage.removeItem('accessToken');
              delete axiosInstance.defaults.headers.common['Authorization'];
              if (authUpdateToken) {
                authUpdateToken(null);
              }

              if (!NO_REDIRECT_ON_REFRESH_FAILURE_PATHS.includes(window.location.pathname)) {
                window.location.href = '/login';
              }

              // Reject all pending requests
              pendingRequestsQueue.forEach((callback) => callback.reject(new Error('No new token received')));
              pendingRequestsQueue = [];

              return Promise.reject(new Error('Token refresh successful but no new token received.'));
            }
          })
          .catch((refreshError) => {
            console.error("[REFRESH] Refresh token request failed:", refreshError.response?.data || refreshError.message);
            let apiErrorMessage;

            if (refreshError.response && refreshError.response.data) {
              if (typeof refreshError.response.data === 'string' && refreshError.response.data.trim() !== '') {
                apiErrorMessage = refreshError.response.data;
              } else if (refreshError.response.data.message && typeof refreshError.response.data.message === 'string' && refreshError.response.data.message.trim() !== '') {
                apiErrorMessage = refreshError.response.data.message;
              } else if (refreshError.response.data.error && typeof refreshError.response.data.error === 'string' && refreshError.response.data.error.trim() !== '') {
                apiErrorMessage = refreshError.response.data.error;
              }
            }

            const displayMessage = apiErrorMessage || 'Your session has expired. Please log in again.';
            toast.error(displayMessage);

            localStorage.removeItem('accessToken');
            delete axiosInstance.defaults.headers.common['Authorization'];

            if (authUpdateToken) {
              authUpdateToken(null);
            }

            if (!NO_REDIRECT_ON_REFRESH_FAILURE_PATHS.includes(window.location.pathname)) {
              window.location.href = '/login';
            }

            // Reject all pending requests
            pendingRequestsQueue.forEach((callback) => callback.reject(refreshError));
            pendingRequestsQueue = [];

            return Promise.reject(refreshError);
          })
          .finally(() => {
            refreshTokenPromise = null;
            console.log("[REFRESH] Refresh token promise settled (cleared).");
          });
      } else {
        console.log("[REFRESH] Another refresh is already in progress. Queuing this request.");
      }

      // Return a promise that waits for refreshTokenPromise and then retries the original request
      return new Promise((resolve, reject) => {
        pendingRequestsQueue.push({
          resolve: async (tokenValue) => {
            try {
              if (tokenValue && tokenValue !== 'null') {
                originalRequest.headers['Authorization'] = `Bearer ${tokenValue}`;
              }
              console.log(`[RETRY] Retrying original request: ${originalRequest.url}`);
              const response = await axiosInstance(originalRequest);
              resolve(response);
            } catch (err) {
              console.error(`[RETRY] Failed to retry original request: ${originalRequest.url}`, err);
              reject(err);
            }
          },
          reject: (err) => {
            console.error(`[RETRY] Original request rejected due to refresh failure: ${originalRequest.url}`, err);
            reject(err);
          }
        });
      });
    }

    console.warn(`[RESPONSE ERROR] Error not handled by refresh logic (conditions not met): URL ${url}, Status ${status}, _retry: ${originalRequest._retry}`);
    return Promise.reject(error);
  }
);


export default axiosInstance;