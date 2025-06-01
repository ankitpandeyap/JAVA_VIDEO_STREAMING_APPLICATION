import '../css/LoadingSpinner.css'; // Create this file ne


export default function LoadingSpinner() {
  return (
    <div className="spinner-wrapper">
      <div className="spinner"></div>
      <p className="loading-text">Loading, please wait...</p>
    </div>
  );
}