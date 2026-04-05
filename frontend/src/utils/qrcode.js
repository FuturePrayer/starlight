import QRCode from 'qrcode'

/**
 * 生成二维码的 Data URL。
 * 自动读取当前主题的 CSS 变量，使二维码颜色适配深色/浅色主题。
 *
 * @param {string} text - 二维码内容（URL 或 otpauth:// URI）
 * @param {number} [size=256] - 图片尺寸（正方形，单位像素）
 * @returns {Promise<string>} Base64 编码的 PNG Data URL
 */
export async function generateQrDataUrl(text, size = 256) {
  const style = getComputedStyle(document.documentElement)
  // 前景色使用主要文本颜色，背景色使用卡片背景色
  const dark = style.getPropertyValue('--sl-text').trim() || '#1a1a1a'
  const light = style.getPropertyValue('--sl-card').trim() || '#ffffff'

  return QRCode.toDataURL(text, {
    width: size,
    margin: 1,
    color: {
      dark,
      light
    },
    errorCorrectionLevel: 'M'
  })
}