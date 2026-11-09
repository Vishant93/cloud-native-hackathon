import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const VUS = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || "30s";

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    checks: ["rate>0.99"],
    http_req_duration: ["p(95)<2000"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const registrationResponse = http.post(
    `${BASE_URL}/api/attendees`,
    JSON.stringify({
      name: `Load test attendee ${__VU}-${__ITER}`,
      email: `loadtest-${__VU}-${__ITER}-${Date.now()}@example.invalid`,
    }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "register_attendee" },
    },
  );

  const registrationOk = check(registrationResponse, {
    "registration returns 201": (response) => response.status === 201,
    "registration returns attendee ID": (response) => {
      if (response.status !== 201) {
        return false;
      }
      try {
        return typeof response.json("attendeeId") === "string";
      } catch {
        return false;
      }
    },
  });

  if (!registrationOk) {
    sleep(1);
    return;
  }

  const attendeeId = registrationResponse.json("attendeeId");
  const checkInResponse = http.post(
    `${BASE_URL}/api/attendees/${encodeURIComponent(attendeeId)}/check-in`,
    null,
    { tags: { name: "check_in_attendee" } },
  );

  check(checkInResponse, {
    "check-in returns 200": (response) => response.status === 200,
    "check-in confirms attendee": (response) => {
      if (response.status !== 200) {
        return false;
      }
      try {
        return response.json("checkedIn") === true;
      } catch {
        return false;
      }
    },
  });

  const summaryResponse = http.get(`${BASE_URL}/api/attendees/summary`, {
    tags: { name: "attendance_summary" },
  });
  check(summaryResponse, {
    "attendance summary returns 200": (response) => response.status === 200,
    "attendance summary has expected fields": (response) => {
      if (response.status !== 200) {
        return false;
      }
      try {
        const summary = response.json();
        return (
          Number.isInteger(summary.totalRegistered) &&
          Number.isInteger(summary.checkedIn) &&
          Number.isInteger(summary.remaining)
        );
      } catch {
        return false;
      }
    },
  });

  sleep(1);
}
