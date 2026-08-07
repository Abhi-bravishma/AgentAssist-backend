/** Minimal toast — one element, no dependencies. */
let timer: number | undefined;

export function toast(msg: string, isError = false) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.className = 'show' + (isError ? ' err' : '');
  window.clearTimeout(timer);
  timer = window.setTimeout(() => { el!.className = ''; }, 3200);
}
