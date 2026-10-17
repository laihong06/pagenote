const choices = new Set(['system', 'light', 'dark']);
const capture = new URLSearchParams(location.search).has('capture');
const system = matchMedia('(prefers-color-scheme: dark)');
const setting = document.getElementById('themeSetting');
const markdownChoices = new Set(['classic', 'paper']);
const markdownSetting = document.getElementById('markdownThemeSetting');
let preference = 'system';
let change = 0;
let markdownPreference = 'classic';
let markdownChange = 0;

function applyMarkdown(value) {
  const previous = markdownPreference;
  markdownPreference = markdownChoices.has(value) ? value : 'classic';
  document.documentElement.dataset.markdownTheme = markdownPreference;
  markdownSetting.value = markdownPreference;
  if (previous !== markdownPreference)
    document.dispatchEvent(new Event('markdown-theme-changed'));
}

export function getMarkdownTheme() {
  return markdownPreference;
}

export async function refreshMarkdownTheme() {
  const initialChange = markdownChange;
  const response = await fetch('/api/preferences');
  if (!response.ok) throw Error('Unable to read Markdown theme');
  const preferences = await response.json();
  if (markdownChange === initialChange)
    applyMarkdown(preferences.markdownTheme);
}

export async function setMarkdownTheme(value) {
  if (!markdownChoices.has(value)) throw Error('Unsupported Markdown theme');
  const currentChange = ++markdownChange;
  try {
    const response = await fetch('/api/preferences', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdownTheme: value }),
    });
    if (!response.ok) throw Error('Unable to save Markdown theme');
    const preferences = await response.json();
    if (markdownChange === currentChange)
      applyMarkdown(preferences.markdownTheme);
  } catch (error) {
    if (markdownChange !== currentChange) return;
    markdownSetting.value = markdownPreference;
    throw error;
  }
}

function apply(value) {
  preference = choices.has(value) ? value : 'system';
  document.documentElement.dataset.theme = capture
    ? 'light'
    : preference === 'system'
      ? system.matches
        ? 'dark'
        : 'light'
      : preference;
  setting.value = preference;
  document.dispatchEvent(new Event('appearance-changed'));
}

export function getTheme() {
  return preference;
}

export async function initializeTheme() {
  const initialChange = change;
  const initialMarkdownChange = markdownChange;
  const response = await fetch('/api/preferences');
  if (!response.ok) throw Error('Unable to read appearance settings');
  const preferences = await response.json();
  if (!capture && change === initialChange) apply(preferences.theme);
  if (markdownChange === initialMarkdownChange)
    applyMarkdown(preferences.markdownTheme);
}

export async function setTheme(value) {
  if (!choices.has(value)) throw Error('Unsupported theme');
  const currentChange = ++change;
  try {
    const response = await fetch('/api/preferences', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ theme: value }),
    });
    if (!response.ok) throw Error('Unable to save appearance settings');
    const preferences = await response.json();
    if (change === currentChange) apply(preferences.theme);
  } catch (error) {
    if (change !== currentChange) return;
    setting.value = preference;
    throw error;
  }
}

system.addEventListener('change', () => {
  if (preference === 'system' && !capture) apply(preference);
});
apply(preference);
applyMarkdown(markdownPreference);
