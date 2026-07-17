import { getAuthenticatedProfile, inspectPortalAccess, requirePortalUser, signOut } from "./auth.js";
import { supabase } from "./supabase-client.js";
import { initializeModule, openDeviceAssignment, openPatientEdit, openPatientEnrollment, openPatientExit } from "./modules.js";

const page = document.body.dataset.page;
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const escapeHtml = (value = "") => String(value).replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character]);
const initials = (name = "User") => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
const formatDateTime = (value) => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
const statusClass = (status = "") => String(status).toLowerCase().replaceAll("_", "-");

function showToast(message) {
  let region = $(".toast-region");
  if (!region) {
    region = document.createElement("div");
    region.className = "toast-region";
    region.setAttribute("aria-live", "polite");
    document.body.append(region);
  }
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  region.append(toast);
  setTimeout(() => toast.remove(), 3400);
}

async function initializeLogin() {
  const existing = await getAuthenticatedProfile();
  if (existing) {
    location.replace(new URL("dashboard.html", document.baseURI));
    return;
  }
  const form = $("#login-form");
  const alert = $("#login-alert");
  $("#password-toggle")?.addEventListener("click", () => {
    const input = $("#password");
    input.type = input.type === "password" ? "text" : "password";
    $("#password-toggle span").textContent = input.type === "password" ? "visibility" : "visibility_off";
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    alert.className = "form-alert hidden";
    const button = $("button[type=submit]", form);
    button.disabled = true;
    button.firstChild.textContent = "Signing in... ";
    const email = $("#email").value.trim();
    const password = $("#password").value;
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) {
      alert.textContent = error.message;
      alert.className = "form-alert error";
      button.disabled = false;
      button.firstChild.textContent = "Sign in ";
      return;
    }
    const access = await inspectPortalAccess();
    if (!access.ok) {
      const accessMessages = {
        profile_missing: "Your login exists, but its public doctor profile has not been created.",
        profile_inactive: `Your portal profile is ${access.profile?.status || "inactive"}. An administrator must activate it.`,
        role_not_allowed: `This account has the ${access.profile?.role || "unknown"} role. Doctor or administrator access is required.`,
        organization_missing: "Your account is not assigned to a clinic organization.",
        profile_query_failed: "The profile could not be read. Confirm that the NeuroVibe schema and RLS migration were applied.",
        session_error: "Supabase could not establish a session. Please retry.",
      };
      alert.textContent = accessMessages[access.code] || "This account is not authorized for the doctor portal.";
      alert.className = "form-alert error";
      await supabase.auth.signOut();
      button.disabled = false;
      button.firstChild.textContent = "Sign in ";
      return;
    }
    const requested = new URLSearchParams(location.search).get("returnTo");
    const destination = requested?.startsWith("/") && !requested.startsWith("//")
      ? new URL(requested, location.origin)
      : new URL("dashboard.html", document.baseURI);
    location.replace(destination);
  });
}

function portalShell(profile) {
  const nav = [
    ["dashboard", "dashboard.html", "dashboard", "Dashboard"],
    ["patients", "patients.html", "group", "Patients"],
    ["devices", "devices.html", "precision_manufacturing", "Devices"],
    ["care-plans", "care-plans.html", "assignment", "Care Plans"],
    ["schedules", "schedules.html", "calendar_month", "Appointments"],
    ["sessions", "sessions.html", "monitor_heart", "Device Usage"],
    ["reports", "reports.html", "assessment", "Reports"],
    ["alerts", "alerts.html", "warning", "Alerts"],
    ["audit", "audit.html", "history", "Audit Log"],
    ["settings", "settings.html", "settings", "Settings"],
  ];
  const activePage = page === "patient" ? "patients" : page;
  $("#sidebar").innerHTML = `
    <div class="sidebar-brand"><span class="brand-mark"><span class="material-symbols-outlined">neurology</span></span><div><strong>NeuroVibe Portal</strong><small>CLINICAL ADMIN</small></div></div>
    <nav aria-label="Primary navigation">${nav.map(([id, href, icon, label]) => `<a class="nav-link ${activePage === id ? "active" : ""} ${href === "#" ? "disabled" : ""}" href="${href}" data-nav="${id}"><span class="material-symbols-outlined">${icon}</span><span>${label}</span></a>`).join("")}</nav>
    <div class="sidebar-bottom"><button class="btn btn-primary" style="width:100%" data-enrollment><span class="material-symbols-outlined">person_add</span>Add patient</button></div>`;
  $("#topbar").innerHTML = `
    <div class="topbar-left"><button class="icon-button mobile-menu" id="mobile-menu" aria-label="Open navigation"><span class="material-symbols-outlined">menu</span></button><div class="global-search"><span class="material-symbols-outlined">search</span><input id="global-search" aria-label="Search" placeholder="Search patient code or name"></div></div>
    <div class="topbar-actions"><a class="icon-button" href="alerts.html" aria-label="Notifications"><span class="material-symbols-outlined">notifications</span></a><a class="icon-button" href="settings.html" aria-label="Help and settings"><span class="material-symbols-outlined">help</span></a><div class="doctor-chip"><span class="avatar">${escapeHtml(initials(profile.full_name || profile.email))}</span><span class="doctor-meta"><strong>${escapeHtml(profile.full_name || profile.email)}</strong><span>${escapeHtml(profile.role === "admin" ? "Administrator" : "Doctor")}</span></span><button class="icon-button" id="sign-out" aria-label="Sign out"><span class="material-symbols-outlined">logout</span></button></div></div>`;

  $("#mobile-menu")?.addEventListener("click", () => $("#sidebar").classList.toggle("open"));
  $("#sign-out")?.addEventListener("click", signOut);
  $$('[data-enrollment]').forEach((button) => button.addEventListener("click", () => openPatientEnrollment({ profile })));
  $("#global-search")?.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && event.target.value.trim()) location.href = `patients.html?q=${encodeURIComponent(event.target.value.trim())}`;
  });
}

async function initializePortal() {
  const auth = await requirePortalUser();
  if (!auth) return;
  portalShell(auth.profile);
  if (page === "dashboard") await loadDashboard();
  if (page === "patients") await loadPatients();
  if (page === "patient") await loadPatientDetail(auth);
  if (!["dashboard", "patients", "patient"].includes(page)) await initializeModule(page, auth);
}

async function loadDashboard() {
  const start = new Date(); start.setHours(0, 0, 0, 0);
  const end = new Date(start); end.setDate(end.getDate() + 1);
  const [patientsResult, assignedResult, availableResult, missedResult, appointmentsResult, faultyResult, auditResult] = await Promise.all([
    supabase.from("patients").select("id", { count: "exact", head: true }).in("program_status", ["enrolled", "active"]),
    supabase.from("devices").select("id", { count: "exact", head: true }).in("lifecycle_status", ["assigned", "active"]),
    supabase.from("devices").select("id", { count: "exact", head: true }).eq("lifecycle_status", "available"),
    supabase.from("appointments").select("id", { count: "exact", head: true }).eq("status", "no_show").gte("scheduled_for", start.toISOString()).lt("scheduled_for", end.toISOString()),
    supabase.from("appointments").select("id, title, appointment_type, scheduled_for, duration_minutes, location, status, patients(id, full_name, patient_code)").gte("scheduled_for", start.toISOString()).lt("scheduled_for", end.toISOString()).order("scheduled_for").limit(8),
    supabase.from("devices").select("id, display_name, lifecycle_status, last_seen_at").eq("lifecycle_status", "faulty").limit(3),
    supabase.from("audit_events").select("id, action, summary, created_at").order("created_at", { ascending: false }).limit(5),
  ]);
  const errors = [patientsResult, assignedResult, availableResult, missedResult, appointmentsResult, faultyResult, auditResult].filter((result) => result.error);
  if (errors.length) showToast("Some dashboard data could not be loaded.");
  $("#active-patients").textContent = patientsResult.count ?? 0;
  $("#assigned-devices").textContent = assignedResult.count ?? 0;
  $("#available-devices").textContent = availableResult.count ?? 0;
  $("#missed-sessions").textContent = missedResult.count ?? 0;
  $("#today-label").textContent = new Intl.DateTimeFormat(undefined, { dateStyle: "full" }).format(new Date());
  renderTodayAppointments(appointmentsResult.data ?? []);
  renderAttention(faultyResult.data ?? []);
  renderActivity(auditResult.data ?? []);
}

function renderTodayAppointments(appointments) {
  const body = $("#today-sessions");
  if (!appointments.length) {
    body.innerHTML = `<tr><td colspan="5"><div class="empty-state"><span class="material-symbols-outlined">event_available</span><h3>No appointments today</h3><p>No patient visits are scheduled.</p></div></td></tr>`;
    return;
  }
  body.innerHTML = appointments.map((item) => `<tr><td><strong>${new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(item.scheduled_for))}</strong><small class="table-sub">${item.duration_minutes} min</small></td><td><div class="person-cell"><span class="person-avatar">${escapeHtml(initials(item.patients?.full_name))}</span><span><strong>${escapeHtml(item.patients?.full_name || "Patient")}</strong><small>${escapeHtml(item.patients?.patient_code || "")}</small></span></div></td><td><strong>${escapeHtml(item.title)}</strong><small class="table-sub">${escapeHtml(item.location || item.appointment_type.replaceAll("_", " "))}</small></td><td><span class="pill ${statusClass(item.status)}">${escapeHtml(item.status)}</span></td><td><a class="icon-button" href="patient.html?id=${encodeURIComponent(item.patients?.id || "")}" aria-label="Open patient"><span class="material-symbols-outlined">open_in_new</span></a></td></tr>`).join("");
}

function renderAttention(devices) {
  const container = $("#attention-list");
  if (!devices.length) {
    container.innerHTML = `<div class="empty-state"><span class="material-symbols-outlined">verified</span><h3>No device alerts</h3><p>All visible devices are clear.</p></div>`;
    return;
  }
  container.innerHTML = devices.map((device) => `<div class="attention-item danger"><span class="material-symbols-outlined">report</span><p><strong>${escapeHtml(device.display_name)}</strong><small>Marked faulty · Last seen ${escapeHtml(formatDateTime(device.last_seen_at))}</small></p></div>`).join("");
}

function renderActivity(events) {
  const container = $("#recent-activity");
  if (!events.length) {
    container.innerHTML = `<div class="empty-state"><span class="material-symbols-outlined">history</span><h3>No recent activity</h3><p>New portal actions will appear here.</p></div>`;
    return;
  }
  container.innerHTML = events.map((event) => `<div class="attention-item"><span class="material-symbols-outlined">history</span><p><strong>${escapeHtml(event.summary)}</strong><small>${escapeHtml(formatDateTime(event.created_at))}</small></p></div>`).join("");
}

let patientRows = [];
async function loadPatients() {
  const result = await supabase.from("patients").select(`id, patient_code, full_name, program_status, updated_at, device_assignments(id, status, device_id, devices(id, display_name, lifecycle_status)), care_plans(id, name, status), appointments(id, scheduled_for, status)`).order("created_at", { ascending: false }).limit(250);
  if (result.error) {
    $("#patient-body").innerHTML = `<tr><td colspan="7"><div class="empty-state"><span class="material-symbols-outlined">error</span><h3>Patients unavailable</h3><p>${escapeHtml(result.error.message)}</p></div></td></tr>`;
    return;
  }
  patientRows = result.data ?? [];
  const query = new URLSearchParams(location.search).get("q") || "";
  $("#patient-search").value = query;
  $("#patient-search").addEventListener("input", renderPatientTable);
  $("#status-filter").addEventListener("change", renderPatientTable);
  $("#device-filter").addEventListener("change", renderPatientTable);
  $("#reset-filters").addEventListener("click", () => { $("#patient-search").value = ""; $("#status-filter").value = "all"; $("#device-filter").value = "all"; renderPatientTable(); });
  $("#export-patients").addEventListener("click", exportPatientsCsv);
  renderPatientTable();
}

function exportPatientsCsv() {
  if (!patientRows.length) { showToast("There are no patient records to export."); return; }
  const quote = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;
  const lines = [["Patient code", "Full name", "Status", "Assigned device", "Active care plan"].map(quote).join(",")];
  patientRows.forEach((patient) => {
    const assignment = patient.device_assignments?.find((item) => item.status === "active");
    const plan = patient.care_plans?.find((item) => item.status === "active");
    lines.push([patient.patient_code, patient.full_name, patient.program_status, assignment?.devices?.display_name || "", plan?.name || ""].map(quote).join(","));
  });
  const url = URL.createObjectURL(new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `neurovibe-patients-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

function renderPatientTable() {
  const search = $("#patient-search").value.trim().toLowerCase();
  const status = $("#status-filter").value;
  const device = $("#device-filter").value;
  const rows = patientRows.filter((patient) => {
    const activeAssignment = patient.device_assignments?.find((assignment) => assignment.status === "active");
    return (!search || `${patient.full_name} ${patient.patient_code}`.toLowerCase().includes(search)) && (status === "all" || patient.program_status === status) && (device === "all" || (device === "assigned") === Boolean(activeAssignment));
  });
  $("#patient-count").textContent = `${rows.length} patient${rows.length === 1 ? "" : "s"}`;
  const body = $("#patient-body");
  if (!rows.length) {
    body.innerHTML = `<tr><td colspan="7"><div class="empty-state"><span class="material-symbols-outlined">person_search</span><h3>No matching patients</h3><p>Change the search or filters.</p></div></td></tr>`;
    return;
  }
  body.innerHTML = rows.map((patient) => {
    const assignment = patient.device_assignments?.find((item) => item.status === "active");
    const plan = patient.care_plans?.find((item) => item.status === "active");
    const next = patient.appointments?.filter((item) => ["scheduled", "confirmed"].includes(item.status) && new Date(item.scheduled_for) > new Date()).sort((a, b) => new Date(a.scheduled_for) - new Date(b.scheduled_for))[0];
    return `<tr data-patient-id="${escapeHtml(patient.id)}"><td><div class="person-cell"><span class="person-avatar">${escapeHtml(initials(patient.full_name))}</span><span><strong>${escapeHtml(patient.full_name)}</strong><small>Updated ${escapeHtml(formatDateTime(patient.updated_at))}</small></span></div></td><td><code>${escapeHtml(patient.patient_code)}</code></td><td><span class="pill ${statusClass(patient.program_status)}">${escapeHtml(patient.program_status)}</span></td><td>${escapeHtml(assignment?.devices?.display_name || "Unassigned")}</td><td>${escapeHtml(plan?.name || "No active plan")}</td><td>${escapeHtml(formatDateTime(next?.scheduled_for))}</td><td><a class="icon-button" href="patient.html?id=${encodeURIComponent(patient.id)}" aria-label="View ${escapeHtml(patient.full_name)}"><span class="material-symbols-outlined">visibility</span></a></td></tr>`;
  }).join("");
}

async function loadPatientDetail(auth) {
  const id = new URLSearchParams(location.search).get("id");
  if (!id) { location.replace(new URL("patients.html", document.baseURI)); return; }
  const [patientResult, sessionsResult] = await Promise.all([
    supabase.from("patients").select(`id, patient_code, full_name, date_of_birth, gender, phone, email, program_status, consent_status, device_assignments(id, status, starts_at, lease_expires_at, devices(id, hardware_id, display_name, lifecycle_status, firmware_version, last_seen_at, pending_record_count)), care_plans(id, name, status, min_hz, target_hz, max_hz, duration_seconds, max_duration_seconds, manual_control_allowed), appointments(id, title, scheduled_for, location, status)`).eq("id", id).maybeSingle(),
    supabase.from("therapy_sessions").select("id, started_at_utc, duration_seconds, requested_hz, estimated_hz, status, completion_reason, sync_source").eq("patient_id", id).order("started_at_utc", { ascending: false }).limit(20),
  ]);
  if (patientResult.error || !patientResult.data) {
    $("#patient-content").innerHTML = `<div class="card empty-state"><span class="material-symbols-outlined">person_off</span><h3>Patient not found</h3><p>The record is unavailable or outside your access.</p><a class="btn btn-primary" href="patients.html">Return to patients</a></div>`;
    return;
  }
  renderPatientDetail(patientResult.data, sessionsResult.data ?? [], auth);
}

function renderPatientDetail(patient, sessions, auth) {
  const assignment = patient.device_assignments?.find((item) => item.status === "active");
  const device = assignment?.devices;
  const plan = patient.care_plans?.find((item) => item.status === "active");
  const completed = sessions.filter((session) => session.status === "completed").length;
  const adherence = sessions.length ? Math.round(completed / sessions.length * 100) : 0;
  $("#patient-name").textContent = patient.full_name;
  $("#patient-name-breadcrumb").textContent = patient.full_name;
  $("#patient-initials").textContent = initials(patient.full_name);
  $("#patient-code").textContent = patient.patient_code;
  $("#patient-status").textContent = patient.program_status;
  $("#patient-status").className = `pill ${statusClass(patient.program_status)}`;
  $("#patient-device-name").textContent = device?.display_name || "No device assigned";
  $("#adherence-value").textContent = `${adherence}%`;
  $("#session-total").textContent = sessions.length;
  $("#session-completed").textContent = completed;
  $("#session-missed").textContent = sessions.length - completed;
  $("#plan-name").textContent = plan?.name || "No active care plan";
  $("#plan-hz").textContent = plan ? `${plan.target_hz} Hz` : "—";
  $("#plan-range").textContent = plan ? `${plan.min_hz}–${plan.max_hz} Hz` : "—";
  $("#plan-duration").textContent = plan ? `${Math.round(plan.duration_seconds / 60)} min` : "—";
  $("#device-title").textContent = device?.display_name || "Unassigned";
  $("#device-status").textContent = device?.lifecycle_status || "No device";
  $("#device-hardware").textContent = device?.hardware_id || "Registers during first secure setup";
  $("#device-lease").textContent = assignment?.lease_expires_at ? formatDateTime(assignment.lease_expires_at) : "Not issued";
  $("#device-firmware").textContent = device?.firmware_version || "—";
  $("#device-sync").textContent = formatDateTime(device?.last_seen_at);
  $("#pending-records").textContent = device?.pending_record_count ?? 0;
  const body = $("#patient-sessions");
  body.innerHTML = sessions.length ? sessions.map((session) => `<tr><td>${escapeHtml(formatDateTime(session.started_at_utc))}</td><td>${escapeHtml(session.sync_source.replaceAll("_", " "))}</td><td>${Math.round(session.duration_seconds / 60)} min</td><td>${escapeHtml(session.requested_hz)} Hz</td><td>${session.estimated_hz ?? "—"} Hz</td><td><span class="pill ${statusClass(session.status)}">${escapeHtml(session.status)}</span></td></tr>`).join("") : `<tr><td colspan="6"><div class="empty-state"><span class="material-symbols-outlined">monitor_heart</span><h3>No device usage recorded</h3><p>Synchronized vibration runs will appear here.</p></div></td></tr>`;
  $("#exit-patient")?.addEventListener("click", () => openPatientExit(auth, patient));
  $("#edit-patient")?.addEventListener("click", () => openPatientEdit(auth, patient));
  $("#replace-device")?.addEventListener("click", () => openDeviceAssignment(auth, patient.id, true));
  $("#assign-device")?.addEventListener("click", () => openDeviceAssignment(auth, patient.id));
  $$(".tab").forEach((tab) => tab.addEventListener("click", () => {
    const destinations = { "Care plan": "care-plans.html", Appointments: "schedules.html", "Device usage": "sessions.html", Device: "devices.html", "Audit history": "audit.html" };
    if (destinations[tab.textContent.trim()]) location.href = destinations[tab.textContent.trim()];
  }));
}

if (page === "login") initializeLogin(); else initializePortal();
