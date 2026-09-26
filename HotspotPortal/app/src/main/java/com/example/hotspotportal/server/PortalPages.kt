package com.example.hotspotportal.server

/** Configurable copy for the guest-facing page. */
data class PortalCopy(
    val title: String = "Wi-Fi Login",
    val welcome: String = "Sign in to use this network.",
    val footer: String = "",
)

/**
 * The guest-facing page, fully self-contained.
 *
 * Every byte is inline: captive webviews on iOS, Windows and Android have no
 * route to the internet at this point, so any CDN reference would hang the
 * page. No ES modules, no framework — plain CSS and XHR, and it stays under
 * 100 KB.
 *
 * The visual language matches the app: hard ink borders, zero corner radius, a
 * solid offset block instead of a blurred shadow, halftone dots behind the card.
 *
 * The accent colours are rationed for contrast, not taste. White on the pop pink
 * is only 3.5:1, which fails AA for the 16px sign-in label, so pink carries no
 * text at all here — it is the decorative strip. The button is ocean blue, which
 * clears 4.9:1 against white at any size, and the katakana chip is lemon, which
 * clears 13.8:1 against ink at any size.
 */
object PortalPages {

    fun portalHtml(copy: PortalCopy, message: String? = null, signedInAs: String? = null): String {
        val error = message?.let { """<p class="err">${esc(it)}</p>""" } ?: ""
        val body = if (signedInAs != null) {
            connectedBody(signedInAs)
        } else {
            formBody(copy.title, copy.welcome, error)
        }
        return """<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>${esc(copy.title)}</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
color:#0A0A0A;background-color:#0077B6;
background-image:radial-gradient(#023E8A 1.6px,transparent 1.6px);background-size:14px 14px;
display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}
.card{width:100%;max-width:400px;background:#fff;border:3px solid #0A0A0A;
box-shadow:8px 8px 0 #0A0A0A;padding:0 20px 20px}
.strip{height:12px;background:#FF2D95;border-bottom:3px solid #0A0A0A;margin:0 -20px 18px}
.mark{display:block;width:96px;height:96px;margin:0 auto 14px;border:3px solid #0A0A0A}
h1{font-size:23px;font-weight:900;margin:0 0 10px;text-align:center;letter-spacing:-.5px}
.chipwrap{text-align:center;margin:0 0 14px}
.chip{display:inline-block;background:#FFD400;border:3px solid #0A0A0A;color:#0A0A0A;
font-size:12px;font-weight:800;letter-spacing:.14em;padding:4px 12px}
p.w{color:#33475B;font-size:14px;margin:0 0 18px;line-height:1.5;text-align:center}
label{display:block;font-size:11px;color:#0A0A0A;margin:0 0 6px;text-transform:uppercase;
letter-spacing:.09em;font-weight:800}
input{width:100%;padding:13px 12px;margin-bottom:14px;border:3px solid #0A0A0A;border-radius:0;
background:#F6FAFD;color:#0A0A0A;font-size:16px}
input:focus{outline:0;background:#fff;box-shadow:4px 4px 0 #0A0A0A}
button{width:100%;padding:16px;border:3px solid #0A0A0A;border-radius:0;background:#0077B6;color:#fff;
font-size:16px;font-weight:800;letter-spacing:.06em;text-transform:uppercase;cursor:pointer;
box-shadow:6px 6px 0 #0A0A0A}
button:active{box-shadow:2px 2px 0 #0A0A0A;transform:translate(4px,4px)}
button:disabled{opacity:.5}
.err{background:#FFD400;border:3px solid #0A0A0A;color:#0A0A0A;font-size:14px;font-weight:700;
margin:0 0 14px;padding:10px 12px}
.ok{background:#0077B6;color:#fff;border:3px solid #0A0A0A;box-shadow:6px 6px 0 #0A0A0A;
font-size:20px;font-weight:900;letter-spacing:.06em;margin:0 0 16px;padding:14px;text-align:center}
.hint{color:#4A5D6E;font-size:12px;margin-top:18px;line-height:1.5;text-align:center}
.foot{color:#fff;font-size:12px;font-weight:700;margin-top:20px;text-align:center;
border-top:3px solid #0A0A0A;padding-top:12px}
</style></head>
<body><div class="card">$body<div class="foot">${esc(copy.footer)}</div></div>
<script>
function uaPath(){
  var ua=navigator.userAgent||'';
  if(/iPhone|iPad|iPod|Macintosh/.test(ua))return '/hotspot-detect.html';
  if(/Windows/.test(ua))return '/connecttest.txt';
  if(/Ubuntu|Linux/.test(ua))return '/canonical.html';
  return '/generate_204';
}
function post(){
  var u=encodeURIComponent(document.getElementById('u').value);
  var p=encodeURIComponent(document.getElementById('p').value);
  var b=document.getElementById('b');b.disabled=true;
  var x=new XMLHttpRequest();
  x.open('POST','/api/login',true);
  x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');
  x.onload=function(){
    var j={};try{j=JSON.parse(x.responseText)}catch(e){}
    if(x.status===200){
      // Hit the OS's own connectivity check so its portal window closes
      // itself without the guest doing anything.
      var w=document.getElementById('formwrap');
      w.innerHTML='<p class="ok">CONNECTED</p><p class="w">You are online. This window will close automatically.</p>';
      var i=document.createElement('iframe');
      i.style.cssText='display:none';i.src=uaPath();
      document.body.appendChild(i);
      setTimeout(function(){window.location=uaPath();},300);
    }else{
      b.disabled=false;
      var e=document.getElementById('err');
      if(e){e.textContent=j.message||'Sign-in failed.';}
      else{location.reload();}
    }
  };
  x.onerror=function(){b.disabled=false;};
  x.send('username='+u+'&password='+p);
}
document.addEventListener('DOMContentLoaded',function(){
  var f=document.getElementById('f');
  if(f)f.addEventListener('submit',function(e){e.preventDefault();post();});
});
</script>
</body></html>"""
    }

    private fun formBody(title: String, welcome: String, error: String) = """
<div id="formwrap">
<div class="strip"></div>
<img class="mark" src="/logo.png" width="96" height="96" alt="">
<h1>${esc(title)}</h1>
<div class="chipwrap"><span class="chip">ログイン</span></div>
<p class="w">${esc(welcome)}</p>
<div id="err" role="alert">$error</div>
<form id="f" autocomplete="on">
<label for="u">Username</label>
<input id="u" name="username" type="text" autocapitalize="off" autocorrect="off" required autofocus>
<label for="p">Password</label>
<input id="p" name="password" type="password" autocomplete="current-password" required>
<button id="b" type="submit">Sign in</button>
</form>
<p class="hint">Access is granted per device and lasts until you sign out or the portal is stopped.</p>
</div>
"""

    private fun connectedBody(user: String) = """
<div id="formwrap">
<div class="strip"></div>
<img class="mark" src="/logo.png" width="96" height="96" alt="">
<p class="ok">CONNECTED</p>
<p class="w">Signed in as <strong>${esc(user)}</strong>. This window will close automatically.</p>
<iframe style="display:none" src="/generate_204"></iframe>
</div>
"""

    /** Every guest-visible string goes through here — the form reflects values. */
    fun esc(s: String?): String = (s ?: "").let { v ->
        buildString(v.length) {
            v.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }
    }
}
