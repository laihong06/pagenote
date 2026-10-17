// UI tests enter through the same first-run button as a new user.
export async function dismissWelcome(window) {
  await window.waitForFunction(() => !!window.pageNotes);
  await window.evaluate(() => window.pageNotes.ready);
  const welcome = window.locator('#helloOverlay');
  if (await welcome.isVisible()) {
    await window.locator('#helloEnter').click();
    await welcome.waitFor({ state: 'hidden' });
  }
}
