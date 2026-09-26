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
 */
object PortalPages {

    fun portalHtml(copy: PortalCopy, message: String? = null, signedInAs: String? = null): String {
        val error = message?.let { """<p class="err">${esc(it)}</p>""" } ?: ""
        val body = if (signedInAs != null) {
            connectedBody(signedInAs)
        } else {
            formBody(copy.welcome, error)
        }
        return """<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>${esc(copy.title)}</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
background:#10131a;color:#e8ecf1;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}
.card{width:100%;max-width:380px}
h1{font-size:24px;font-weight:600;margin:0 0 8px}
p.w{color:#9aa4b2;font-size:14px;margin:0 0 24px;line-height:1.5}
label{display:block;font-size:12px;color:#9aa4b2;margin:0 0 6px;text-transform:uppercase;letter-spacing:.06em}
input{width:100%;padding:13px 14px;margin-bottom:16px;border-radius:10px;border:1px solid #2a3040;
background:#171b24;color:#e8ecf1;font-size:16px}
input:focus{outline:2px solid #4a9eff;outline-offset:1px;border-color:#4a9eff}
button{width:100%;padding:14px;border:0;border-radius:10px;background:#4a9eff;color:#fff;
font-size:16px;font-weight:600;cursor:pointer}
button:disabled{opacity:.55}
.err{color:#ff6b6b;font-size:14px;margin:-6px 0 14px}
.ok{color:#3ddc97;font-size:18px;font-weight:600;margin:0 0 8px}
.hint{color:#6b7684;font-size:12px;margin-top:20px;line-height:1.5}
.foot{color:#6b7684;font-size:12px;margin-top:28px;text-align:center}
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
      w.innerHTML='<p class="ok">Connected</p><p class="w">You are online. This window will close automatically.</p>';
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

    private fun formBody(welcome: String, error: String) = """
<div id="formwrap">
<p class="w">${esc(welcome)}</p>
<div id="err" role="alert">$error</div>
<form id="f" autocomplete="on">
<label for="u">Username</label>
<input id="u" name="username" type="text" autocapitalize="off" autocorrect="off" required autofocus>
<label for="p">Password</label>
<input id="p" name="password" type="password" autocomplete="current-password" required>
<button id="b" type="submit">Sign in</button>
</form>
<p class="hint">Access is granted per device and expires automatically.</p>
</div>
"""

    private fun connectedBody(user: String) = """
<div class="ok">Connected</div>
<p class="w">Signed in as <strong>${esc(user)}</strong>. This window will close automatically.</p>
<iframe style="display:none" src="/generate_204"></iframe>
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
