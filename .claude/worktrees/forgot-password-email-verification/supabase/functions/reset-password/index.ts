import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { hashCode, jsonResponse } from "../_shared/codes.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve(async (req) => {
  try {
    const { email, code, newPassword } = await req.json();
    if (!email || !code || !newPassword || newPassword.length < 6) {
      return jsonResponse({ ok: false, error: "Invalid request" }, 400);
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const codeHash = await hashCode(code);

    const { data: row } = await supabase
      .from("auth_codes")
      .select("id, code_hash, expires_at")
      .eq("email", email)
      .eq("purpose", "reset_password")
      .is("used_at", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!row || row.code_hash !== codeHash) {
      return jsonResponse({ ok: false, error: "Incorrect code" }, 400);
    }
    if (new Date(row.expires_at) < new Date()) {
      return jsonResponse({ ok: false, error: "Code has expired" }, 400);
    }

    const salt = generateSalt();
    const passwordHash = await hashPassword(newPassword, salt);

    await supabase.from("auth_codes").update({ used_at: new Date().toISOString() }).eq("id", row.id);
    await supabase
      .from("app_users")
      .update({ password_hash: passwordHash, password_salt: salt })
      .eq("email", email);

    return jsonResponse({ ok: true });
  } catch (e) {
    console.error(e);
    return jsonResponse({ ok: false, error: "Unexpected error" }, 500);
  }
});

function generateSalt(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Must match AuthRepository.hashPassword in the Android app exactly:
// SHA-256 over (salt bytes ++ password bytes), hex-encoded.
async function hashPassword(password: string, salt: string): Promise<string> {
  const data = new TextEncoder().encode(salt + password);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
