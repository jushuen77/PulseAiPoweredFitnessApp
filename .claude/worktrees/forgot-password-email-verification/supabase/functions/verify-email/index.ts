import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { hashCode, jsonResponse } from "../_shared/codes.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve(async (req) => {
  try {
    const { email, code } = await req.json();
    if (!email || !code) return jsonResponse({ ok: false, error: "Invalid request" }, 400);

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const codeHash = await hashCode(code);

    const { data: row } = await supabase
      .from("auth_codes")
      .select("id, code_hash, expires_at")
      .eq("email", email)
      .eq("purpose", "verify_email")
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

    await supabase.from("auth_codes").update({ used_at: new Date().toISOString() }).eq("id", row.id);
    await supabase.from("app_users").update({ email_verified: true }).eq("email", email);

    return jsonResponse({ ok: true });
  } catch (e) {
    console.error(e);
    return jsonResponse({ ok: false, error: "Unexpected error" }, 500);
  }
});
