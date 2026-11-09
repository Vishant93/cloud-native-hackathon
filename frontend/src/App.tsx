import { Html5QrcodeScanner } from "html5-qrcode";
import { useCallback, useEffect, useRef, useState } from "react";

type CheckInResult = {
  attendeeId: string;
  name: string;
  checkedIn: boolean;
  checkedInAt: string | null;
};

type ScanState =
  | { kind: "idle"; message: string }
  | { kind: "loading"; message: string }
  | { kind: "success"; message: string; attendee: CheckInResult }
  | { kind: "error"; message: string };

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function extractAttendeeToken(decodedText: string): string | null {
  const value = decodedText.trim();
  if (UUID_PATTERN.test(value)) {
    return value;
  }

  try {
    const url = new URL(value);
    const match = url.pathname.match(/^\/check-in\/([0-9a-f-]{36})\/?$/i);
    return match && UUID_PATTERN.test(match[1]) ? match[1] : null;
  } catch {
    return null;
  }
}

async function submitCheckIn(token: string): Promise<CheckInResult> {
  const response = await fetch(
    `/api/attendees/${encodeURIComponent(token)}/check-in`,
    { method: "POST" },
  );

  if (!response.ok) {
    let message = `Check-in failed (${response.status})`;
    try {
      const problem = (await response.json()) as { detail?: string; message?: string };
      message = problem.detail ?? problem.message ?? message;
    } catch {
      // Keep the HTTP status message when the server response is not JSON.
    }
    throw new Error(message);
  }

  return (await response.json()) as CheckInResult;
}

export default function App() {
  const [scanState, setScanState] = useState<ScanState>({
    kind: "idle",
    message: "Point the camera at an attendee QR code.",
  });
  const processingRef = useRef(false);

  const handleScan = useCallback(async (decodedText: string) => {
    if (processingRef.current) {
      return;
    }

    const token = extractAttendeeToken(decodedText);
    if (!token) {
      setScanState({
        kind: "error",
        message: "This QR code is not a valid attendee check-in code.",
      });
      return;
    }

    processingRef.current = true;
    setScanState({ kind: "loading", message: "Validating attendee…" });
    try {
      const attendee = await submitCheckIn(token);
      setScanState({
        kind: "success",
        message: "Attendee checked in successfully.",
        attendee,
      });
    } catch (error) {
      setScanState({
        kind: "error",
        message: error instanceof Error ? error.message : "Unable to check in attendee.",
      });
    } finally {
      processingRef.current = false;
    }
  }, []);

  useEffect(() => {
    const scanner = new Html5QrcodeScanner(
      "qr-reader",
      { fps: 10, qrbox: { width: 250, height: 250 }, rememberLastUsedCamera: true },
      false,
    );
    scanner.render((decodedText) => void handleScan(decodedText), () => undefined);

    return () => {
      void scanner.clear().catch((error: unknown) => {
        console.error("Could not stop the QR scanner cleanly", error);
      });
    };
  }, [handleScan]);

  return (
    <main className="page-shell">
      <header className="topbar">
        <div className="brand-mark" aria-hidden="true">EP</div>
        <div>
          <p className="eyebrow">EVENTPULSE</p>
          <h1>Attendee check-in</h1>
        </div>
        <span className="staff-badge">STAFF SCANNER</span>
      </header>

      <section className="scanner-layout" aria-label="Attendee QR scanner">
        <div className="scanner-card">
          <div className="card-heading">
            <div>
              <p className="eyebrow">SCAN TICKET</p>
              <h2>Verify attendee</h2>
            </div>
            <span className="live-indicator"><span /> CAMERA READY</span>
          </div>
          <div id="qr-reader" className="qr-reader" />
          <p className={`status-message status-${scanState.kind}`} role="status" aria-live="polite">
            <span className="status-icon" aria-hidden="true">
              {scanState.kind === "success" ? "✓" : scanState.kind === "error" ? "!" : scanState.kind === "loading" ? "…" : "i"}
            </span>
            {scanState.message}
          </p>
        </div>

        <aside className="details-card">
          <p className="eyebrow">CHECK-IN FLOW</p>
          <h2>Ready at the door</h2>
          <ol className="steps">
            <li><span>01</span><div><strong>Allow camera access</strong><small>Use your device camera when prompted.</small></div></li>
            <li><span>02</span><div><strong>Scan the attendee QR</strong><small>Keep the code inside the camera frame.</small></div></li>
            <li><span>03</span><div><strong>Confirm the result</strong><small>Attendee details are validated by the server.</small></div></li>
          </ol>
          {scanState.kind === "success" && (
            <div className="attendee-result">
              <span className="result-check" aria-hidden="true">✓</span>
              <div>
                <small>CHECKED IN</small>
                <strong>{scanState.attendee.name}</strong>
                <code>{scanState.attendee.attendeeId}</code>
              </div>
            </div>
          )}
        </aside>
      </section>
      <footer>Scan securely · Attendee IDs are verified by EventPulse</footer>
    </main>
  );
}
