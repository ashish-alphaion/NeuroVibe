export class HttpError extends Error {
  constructor(status, code, message, details = undefined) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 1_000_000) throw new HttpError(413, 'payload_too_large', 'Request body is too large.');
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'invalid_json', 'Request body must be valid JSON.');
  }
}

export function sendJson(response, status, payload, headers = {}) {
  const body = JSON.stringify(payload, null, 2);
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    ...headers,
  });
  response.end(body);
}

export function requireFields(body, fields) {
  const missing = fields.filter((field) => body[field] === undefined || body[field] === null || body[field] === '');
  if (missing.length) throw new HttpError(400, 'validation_error', `Missing required fields: ${missing.join(', ')}`, { missing });
}

export function asNumber(value, field) {
  const number = Number(value);
  if (!Number.isFinite(number)) throw new HttpError(400, 'validation_error', `${field} must be a number.`);
  return number;
}

