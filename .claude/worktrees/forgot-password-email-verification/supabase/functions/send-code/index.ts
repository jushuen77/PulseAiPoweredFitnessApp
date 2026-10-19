import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { generateCode, hashCode, jsonResponse } from "../_shared/codes.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const resendApiKey = Deno.env.get("RESEND_API_KEY")!;
const fromAddress = Deno.env.get("RESEND_FROM_ADDRESS")!;

Deno.serve(async (req) => {
  try {
    const { email, purpose } = await req.json();
    if (!email || !["verify_email", "reset_password"].includes(purpose)) {
      return jsonResponse({ ok: false, error: "Invalid request" }, 400);
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const { data: user } = await supabase
      .from("app_users")
      .select("uid, email_verified")
      .eq("email", email)
      .maybeSingle();

    if (!user) {
      return jsonResponse({ ok: false, error: "No account with that email" }, 404);
    }
    if (purpose === "verify_email" && user.email_verified) {
      return jsonResponse({ ok: false, error: "Email is already verified" }, 400);
    }

    const code = generateCode();
    const codeHash = await hashCode(code);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

    await supabase.from("auth_codes").insert({
      email,
      code_hash: codeHash,
      purpose,
      expires_at: expiresAt,
    });

    const subject = purpose === "verify_email"
      ? "Verify your Pulse account"
      : "Reset your Pulse password";
    const text = `Your code is ${code}. It expires in 10 minutes.`;

    const emailResponse = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ from: fromAddress, to: email, subject, text }),
    });

    if (!emailResponse.ok) {
      console.error("Resend error:", await emailResponse.text());
      return jsonResponse({ ok: false, error: "Failed to send email" }, 502);
    }

    return jsonResponse({ ok: true });
  } catch (e) {
    console.error(e);
    return jsonResponse({ ok: false, error: "Unexpected error" }, 500);
  }
});
