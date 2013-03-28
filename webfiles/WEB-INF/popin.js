var nativeResizeToFunction = window.resizeTo;
function waitForBody() {
  var body = document.getElementsByTagName('body')[0];
  if (body == null) {
    window.setTimeout('waitForBody()', 25);
    return;
  }
  window.setTimeout('adjustPopinSizeAndLinkIFrames()', 25);
}
function getDocWidth() {
  return Math.max(
    Math.max(document.body.scrollWidth, document.documentElement.scrollWidth),
    Math.max(document.body.offsetWidth, document.documentElement.offsetWidth),
    Math.max(document.body.clientWidth, document.documentElement.clientWidth)
  );
}  
function getDocHeight() {
  return Math.max(
    Math.max(document.body.scrollHeight, document.documentElement.scrollHeight),
    Math.max(document.body.offsetHeight, document.documentElement.offsetHeight),
    Math.max(document.body.clientHeight, document.documentElement.clientHeight)
  );
}
function adjustPopinSizeAndLinkIFrames() {
  if (window.resizeTo == nativeResizeToFunction) {
    window.setTimeout('adjustPopinSizeAndLinkIFrames()', 25);
    return;
  }
  var docWidth = getDocWidth();
  var docHeight = getDocHeight();
  if (docWidth > 1024)
    docHeight += 50;
  if (docHeight > 768)
    docWidth += 50;
  
  var dspWidth = 0;
  var dspHeight = 0;
  if (document.body && document.body.offsetWidth) {
    dspWidth = document.body.offsetWidth;
    dspHeight = document.body.offsetHeight;
  }
  if (document.compatMode == 'CSS1Compat' && document.documentElement && document.documentElement.offsetWidth) {
    dspWidth = document.documentElement.offsetWidth;
    dspHeight = document.documentElement.offsetHeight;
  }
  if (window.innerWidth && window.innerHeight) {
    dspWidth = window.innerWidth;
    dspHeight = window.innerHeight;
  }
  if ((dspWidth * dspHeight) < 1)
    return;
  
  var wWidth = Math.min(docWidth, 1024);
  var wHeight = Math.min(docHeight, 768);
  window.resizeTo(wWidth, wHeight);
  
  var ifrs = document.getElementsByTagName('iframe');
  if (ifrs == null)
    return;
  for (var i = 0; i < ifrs.length; i++)
    setIframeFunctions(ifrs[i]);
}
function setIframeFunctions(ifr) {
  ifr.onload = function() {
    ifr.contentWindow.open = window.open;
    var ifrs = ifr.contentWindow.document.getElementsByTagName('iframe');
    if (ifrs == null)
      return;
    for (var i = 0; i < ifrs.length; i++)
      setIframeFunctions(ifrs[i]);
  }
  ifr.contentWindow.open = window.open;
}
waitForBody();