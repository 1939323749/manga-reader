// Display names for the language codes extensions use
const special = {
  all: '多语言',
  other: '其他',
  multi: '多语言',
  'zh-Hans': '简体中文',
  'zh-Hant': '繁體中文',
  'es-419': '西班牙语（拉美）',
  'pt-BR': '葡萄牙语（巴西）',
}

let display = null
try {
  display = new Intl.DisplayNames(['zh-CN'], { type: 'language' })
} catch {
  display = null
}

export function langName(code) {
  if (special[code]) return special[code]
  try {
    const name = display?.of(code)
    if (name && name !== code) return name
  } catch {
    // invalid tag
  }
  return code
}

// Puts the reader's own languages first, then the rest alphabetically by name
const preferred = ['zh', 'zh-Hans', 'zh-Hant', 'en', 'ja', 'ko', 'all']

export function compareLang(a, b) {
  const ia = preferred.indexOf(a)
  const ib = preferred.indexOf(b)
  if (ia !== -1 || ib !== -1) return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib)
  return langName(a).localeCompare(langName(b), 'zh-CN')
}
