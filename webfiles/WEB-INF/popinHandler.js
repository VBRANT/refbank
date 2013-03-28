window.open = function(url, name, features) {
  return openPopIn(url, name, features);
};
function openPopIn(url, name, features) {
  var body = document.getElementsByTagName('body')[0];
  
  var screen = document.createElement('div');
  screen.style.position = 'fixed';
  screen.style.width = '100%';
  screen.style.height = '100%';
  screen.style.left = '0';
  screen.style.top = '0';
  screen.className = 'popinScreen';
  body.appendChild(screen);
  
  var closeButton = document.createElement('input');
  closeButton.style.position = 'fixed';
  closeButton.style.right = '0';
  closeButton.style.top = '0';
  closeButton.type = 'button';
  closeButton.value = '[X]';
  closeButton.className = 'popinClose';
  
  var frame = document.createElement('iframe');
  frame.style.width = '100%';
  frame.style.height = '100%';
  frame.className = 'popinFrame';
  
  var frameBox = document.createElement('div');
  frameBox.style.position = 'fixed';
  frameBox.style.width = '100px';
  frameBox.style.height = '100px';
  frameBox.style.left = ((100 - ((100 * 100) / window.innerWidth)) / 2) + '%';
  frameBox.style.top = ((100 - ((100 * 100) / window.innerWidth)) / 2) + '%';
  frameBox.style.opacity = '1';
  frameBox.style.backgroundColor = 'FFFFFF';
  frameBox.className = 'popinBox';
  
  frameBox.appendChild(frame);
  body.appendChild(frameBox);
  body.appendChild(closeButton);
  
  frame.onload = function() {
    frame.contentWindow.close = function() {
      frame.contentWindow.closed = 'true';
      body.removeChild(frameBox);
      body.removeChild(screen);
      body.removeChild(closeButton);
    };
    frame.contentWindow.resizeTo = function(width, height) {
      width = Math.min(width, (window.innerWidth - 50));
      height = Math.min(height, (window.innerHeight - 50));
      frameBox.style.width = width + 'px';
      frameBox.style.height = height + 'px';
      frameBox.style.left = ((100 - ((width * 100) / window.innerWidth)) / 2) + '%';
      frameBox.style.top = ((100 - ((height * 100) / window.innerHeight)) / 2) + '%';
    };
    frame.contentWindow.open = function(url, name, features) {
      return openPopIn(url, name, features);
    };
  };
  
  closeButton.onclick = function() {
    if (!confirm('You should close dialogs with their own native controls.\nThis button is a fail-safe only. Proceed?'))
      return;
    body.removeChild(frameBox);
    body.removeChild(screen);
    body.removeChild(closeButton);
  }
  
  setAttribute(frame, 'src', url);
  
  return frame.contentWindow;
}
function setAttribute(element, name, value) {
  var an = document.createAttribute(name);
  an.nodeValue = value;
  element.setAttributeNode(an);
}