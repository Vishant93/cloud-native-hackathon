import { Html5QrcodeScanner } from "html5-qrcode";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";

type CheckInResult = {
  attendeeId: string;
  name: string;
  checkedIn: boolean;
  checkedInAt: string | null;
};

type RegistrationResult = {
  attendeeId: string;
  name: string;
  email: string;
  checkInUrl: string;
  qrCodePngBase64: string;
};

type AttendanceSummary = {
  totalRegistered: number;
  checkedIn: number;
  remaining: number;
};

type ScanState =
  | { kind: "idle"; message: string }
  | { kind: "loading"; message: string }
  | { kind: "success"; message: string; attendee: CheckInResult }
  | { kind: "error"; message: string };

type RegistrationState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "success"; attendee: RegistrationResult }
  | { kind: "error"; message: string };

type DashboardState =
  | { kind: "loading" }
  | { kind: "loaded"; summary: AttendanceSummary }
  | { kind: "error"; message: string };

type View = "register" | "scanner" | "dashboard";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const API_BASE_URL = `${import.meta.env.BASE_URL}api`;

async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string; message?: string };
    return problem.detail ?? problem.message ?? fallback;
  } catch {
    return fallback;
  }
}

function extractAttendeeToken(decodedText: string): string | null {
  const value = decodedText.trim();
  if (UUID_PATTERN.test(value)) {
    return value;
  }

  try {
    const url = new URL(value);
    const match = url.pathname.match(/(?:^|\/)check-in\/([0-9a-f-]{36})\/?$/i);
    return match && UUID_PATTERN.test(match[1]) ? match[1] : null;
  } catch {
    return null;
  }
}

function formatCheckInTime(value: string | null): string {
  if (!value) {
    return "Just now";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

async function submitCheckIn(token: string): Promise<CheckInResult> {
  const response = await fetch(
    `${API_BASE_URL}/attendees/${encodeURIComponent(token)}/check-in`,
    { method: "POST" },
  );

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `Check-in failed (${response.status})`));
  }

  return (await response.json()) as CheckInResult;
}

async function submitRegistration(name: string, email: string): Promise<RegistrationResult> {
  const response = await fetch(`${API_BASE_URL}/attendees`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, email }),
  });

  if (!response.ok) {
    throw new Error(
      await readErrorMessage(response, `Registration failed (${response.status})`),
    );
  }

  return (await response.json()) as RegistrationResult;
}

async function fetchAttendanceSummary(): Promise<AttendanceSummary> {
  const response = await fetch(`${API_BASE_URL}/attendees/summary`);
  if (!response.ok) {
    throw new Error(
      await readErrorMessage(response, `Could not load attendance (${response.status})`),
    );
  }
  return (await response.json()) as AttendanceSummary;
}

export default function App() {
  const [view, setView] = useState<View>("register");
  const [dashboardState, setDashboardState] = useState<DashboardState>({ kind: "loading" });
  const [registrationState, setRegistrationState] = useState<RegistrationState>({
    kind: "idle",
  });
  const [scanState, setScanState] = useState<ScanState>({
    kind: "idle",
    message: "Point the camera at an attendee QR code.",
  });
  const processingRef = useRef(false);

  const loadDashboard = useCallback(async () => {
    setDashboardState({ kind: "loading" });
    try {
      const summary = await fetchAttendanceSummary();
      setDashboardState({ kind: "loaded", summary });
    } catch (error) {
      setDashboardState({
        kind: "error",
        message: error instanceof Error ? error.message : "Unable to load attendance.",
      });
    }
  }, []);

  const handleRegistration = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const name = String(formData.get("name") ?? "").trim();
    const email = String(formData.get("email") ?? "").trim();
    setRegistrationState({ kind: "loading" });

    try {
      const attendee = await submitRegistration(name, email);
      setRegistrationState({ kind: "success", attendee });
      form.reset();
    } catch (error) {
      setRegistrationState({
        kind: "error",
        message: error instanceof Error ? error.message : "Unable to register attendee.",
      });
    }
  };

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
    if (view !== "scanner") {
      return;
    }

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
  }, [handleScan, view]);

  useEffect(() => {
    if (view === "dashboard") {
      void loadDashboard();
    }
  }, [loadDashboard, view]);

  return (
    <main className="page-shell">
      <header className="topbar">
        <div className="brand-mark" aria-hidden="true">EP</div>
        <div>
          <p className="eyebrow">EVENTPULSE</p>
          <h1>Event registration</h1>
        </div>
        <nav className="view-switch" aria-label="Event tools">
          <button
            type="button"
            className={view === "register" ? "view-button active" : "view-button"}
            aria-current={view === "register" ? "page" : undefined}
            onClick={() => setView("register")}
          >
            Register
          </button>
          <button
            type="button"
            className={view === "scanner" ? "view-button active" : "view-button"}
            aria-current={view === "scanner" ? "page" : undefined}
            onClick={() => setView("scanner")}
          >
            Staff scanner
          </button>
          <button
            type="button"
            className={view === "dashboard" ? "view-button active" : "view-button"}
            aria-current={view === "dashboard" ? "page" : undefined}
            onClick={() => setView("dashboard")}
          >
            Dashboard
          </button>
        </nav>
      </header>

      {view === "register" ? (
        <section className="registration-layout" aria-labelledby="registration-title">
          <div className="registration-card">
            <p className="eyebrow">JOIN THE EVENT</p>
            <h2 id="registration-title">Reserve your spot</h2>
            <p className="registration-intro">
              Enter your details to register and receive your personal check-in QR code.
            </p>
            <form className="registration-form" onSubmit={(event) => void handleRegistration(event)}>
              <label htmlFor="attendee-name">Full name</label>
              <input
                id="attendee-name"
                name="name"
                type="text"
                autoComplete="name"
                maxLength={255}
                placeholder="Your name"
                required
              />
              <label htmlFor="attendee-email">Email address</label>
              <input
                id="attendee-email"
                name="email"
                type="email"
                autoComplete="email"
                maxLength={320}
                placeholder="you@example.com"
                required
              />
              <button
                className="submit-button"
                type="submit"
                disabled={registrationState.kind === "loading"}
              >
                {registrationState.kind === "loading" ? "Registering…" : "Register"}
              </button>
            </form>

            {registrationState.kind === "error" && (
              <p className="registration-error" role="alert">{registrationState.message}</p>
            )}
          </div>

          {registrationState.kind === "success" ? (
            <aside className="registration-result" aria-live="polite">
              <p className="eyebrow">REGISTRATION CONFIRMED</p>
              <h2>You’re on the list, {registrationState.attendee.name}.</h2>
              <p className="result-email">{registrationState.attendee.email}</p>
              <img
                className="registration-qr"
                src={`data:image/png;base64,${registrationState.attendee.qrCodePngBase64}`}
                alt={`Check-in QR code for ${registrationState.attendee.name}`}
              />
              <p className="qr-help">Save this QR code and present it at the entrance.</p>
              <a
                className="check-in-link"
                href={registrationState.attendee.checkInUrl}
                target="_blank"
                rel="noreferrer"
              >
                Open check-in link
              </a>
            </aside>
          ) : (
            <aside className="registration-aside">
              <span className="aside-icon" aria-hidden="true">✓</span>
              <h2>Your ticket, ready to scan</h2>
              <p>
                After registration, your unique QR code will be shown here. Staff can scan it
                at the event entrance.
              </p>
            </aside>
          )}
        </section>
      ) : view === "scanner" ? (
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
            {scanState.kind !== "idle" && (
              <div className={`verification-panel verification-${scanState.kind}`} role="status" aria-live="polite">
                <span className="verification-icon" aria-hidden="true">
                  {scanState.kind === "success" ? "✓" : scanState.kind === "error" ? "!" : "…"}
                </span>
                <div className="verification-content">
                  <p className="eyebrow">
                    {scanState.kind === "success"
                      ? "VERIFIED"
                      : scanState.kind === "error"
                        ? "NOT VERIFIED"
                        : "VERIFYING"}
                  </p>
                  {scanState.kind === "success" ? (
                    <>
                      <h3>Check-in complete</h3>
                      <p className="verification-name">{scanState.attendee.name}</p>
                      <p className="verification-time">
                        Checked in <time dateTime={scanState.attendee.checkedInAt ?? undefined}>
                          {formatCheckInTime(scanState.attendee.checkedInAt)}
                        </time>
                      </p>
                    </>
                  ) : (
                    <>
                      <h3>{scanState.kind === "error" ? "Check-in unsuccessful" : "Checking ticket…"}</h3>
                      <p className="verification-message">{scanState.message}</p>
                    </>
                  )}
                </div>
              </div>
            )}
          </aside>
        </section>
      ) : (
        <section className="attendance-dashboard" aria-labelledby="dashboard-title">
          <div className="dashboard-heading">
            <div>
              <p className="eyebrow">LIVE ATTENDANCE</p>
              <h2 id="dashboard-title">Event overview</h2>
            </div>
            <button
              type="button"
              className="refresh-button"
              onClick={() => void loadDashboard()}
              disabled={dashboardState.kind === "loading"}
            >
              {dashboardState.kind === "loading" ? "Refreshing…" : "Refresh"}
            </button>
          </div>
          {dashboardState.kind === "loading" ? (
            <p className="dashboard-message" role="status">Loading attendance…</p>
          ) : dashboardState.kind === "error" ? (
            <p className="dashboard-error" role="alert">{dashboardState.message}</p>
          ) : (
            <div className="attendance-metrics" aria-live="polite">
              <article className="metric-card">
                <p className="eyebrow">REGISTERED</p>
                <strong>{dashboardState.summary.totalRegistered}</strong>
                <span>attendees</span>
              </article>
              <article className="metric-card metric-checked-in">
                <p className="eyebrow">CHECKED IN</p>
                <strong>{dashboardState.summary.checkedIn}</strong>
                <span>attendees</span>
              </article>
              <article className="metric-card metric-remaining">
                <p className="eyebrow">REMAINING</p>
                <strong>{dashboardState.summary.remaining}</strong>
                <span>attendees</span>
              </article>
            </div>
          )}
        </section>
      )}
      <footer>EventPulse · Attendee details are stored securely</footer>
    </main>
  );
}
