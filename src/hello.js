// Keep the first-run welcome out of offscreen document/capture windows.
const capture = new URLSearchParams(location.search).has('capture');
const dialog = document.getElementById('helloOverlay');
const enter = document.getElementById('helloEnter');
const help = document.getElementById('help');
let closing = false;
let returnToHelp = false;

export function showWelcome(fromHelp = false) {
  if (capture || closing || dialog.open) return false;
  returnToHelp = fromHelp;
  if (help.open) help.close();
  dialog.classList.remove('writing', 'leaving');
  dialog.showModal();
  // Reset the stroke animations every time Help reopens the welcome.
  void dialog.offsetWidth;
  dialog.classList.add('writing');
  enter.focus();
  return true;
}

async function welcomeState(method = 'GET') {
  const response = await fetch('/api/welcome', { method });
  if (!response.ok) throw Error('Unable to save or read welcome state');
  return response.json();
}

export async function showFirstWelcome() {
  if (capture) return;
  if (!(await welcomeState()).seen) showWelcome();
}

async function dismiss() {
  if (closing) return;
  closing = true;
  enter.disabled = true;
  try {
    await welcomeState('PUT');
  } catch {
    closing = false;
    enter.disabled = false;
    document.getElementById('helloStatus').hidden = false;
    return;
  }
  document.getElementById('helloStatus').hidden = true;
  dialog.classList.add('leaving');
  await Promise.all(
    dialog
      .getAnimations({ subtree: true })
      .filter((animation) => animation.animationName === 'hello-leave')
      .map((animation) => animation.finished.catch(() => {})),
  );
  dialog.close();
  dialog.classList.remove('writing', 'leaving');
  closing = false;
  enter.disabled = false;
  if (returnToHelp) {
    help.showModal();
    document.getElementById('reopenHello').focus();
  }
}

enter.addEventListener('click', dismiss);
dialog.addEventListener('cancel', (event) => {
  event.preventDefault();
  dismiss();
});
document.getElementById('reopenHello').onclick = () => showWelcome(true);
