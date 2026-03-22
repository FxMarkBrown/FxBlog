const svgToDataUri = (svg: string) => `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`

export const IMAGE_LOADING_PLACEHOLDER = svgToDataUri(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 90">' +
    '<rect width="160" height="90" fill="#f3f4f6"/>' +
    '<circle cx="80" cy="45" r="14" fill="none" stroke="#94a3b8" stroke-width="6" stroke-dasharray="60 28">' +
      '<animateTransform attributeName="transform" type="rotate" from="0 80 45" to="360 80 45" dur="1s" repeatCount="indefinite"/>' +
    '</circle>' +
  '</svg>'
)

export const IMAGE_ERROR_PLACEHOLDER = svgToDataUri(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 90">' +
    '<rect width="160" height="90" fill="#e5e7eb"/>' +
    '<path d="M30 62l22-22 16 16 18-24 24 30H30z" fill="#94a3b8"/>' +
    '<circle cx="58" cy="34" r="7" fill="#cbd5e1"/>' +
  '</svg>'
)

export const WECHAT_QR_PLACEHOLDER = svgToDataUri(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240">' +
    '<rect width="240" height="240" rx="20" fill="#ffffff"/>' +
    '<rect x="18" y="18" width="204" height="204" rx="12" fill="#f8fafc" stroke="#cbd5e1" stroke-width="4"/>' +
    '<rect x="38" y="38" width="46" height="46" fill="#111827"/>' +
    '<rect x="48" y="48" width="26" height="26" fill="#ffffff"/>' +
    '<rect x="56" y="56" width="10" height="10" fill="#111827"/>' +
    '<rect x="156" y="38" width="46" height="46" fill="#111827"/>' +
    '<rect x="166" y="48" width="26" height="26" fill="#ffffff"/>' +
    '<rect x="174" y="56" width="10" height="10" fill="#111827"/>' +
    '<rect x="38" y="156" width="46" height="46" fill="#111827"/>' +
    '<rect x="48" y="166" width="26" height="26" fill="#ffffff"/>' +
    '<rect x="56" y="174" width="10" height="10" fill="#111827"/>' +
    '<rect x="112" y="42" width="10" height="10" fill="#111827"/>' +
    '<rect x="128" y="42" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="58" width="10" height="10" fill="#111827"/>' +
    '<rect x="120" y="58" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="58" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="74" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="74" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="104" width="10" height="10" fill="#111827"/>' +
    '<rect x="120" y="104" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="104" width="10" height="10" fill="#111827"/>' +
    '<rect x="152" y="104" width="10" height="10" fill="#111827"/>' +
    '<rect x="88" y="120" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="120" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="120" width="10" height="10" fill="#111827"/>' +
    '<rect x="168" y="120" width="10" height="10" fill="#111827"/>' +
    '<rect x="88" y="136" width="10" height="10" fill="#111827"/>' +
    '<rect x="120" y="136" width="10" height="10" fill="#111827"/>' +
    '<rect x="152" y="136" width="10" height="10" fill="#111827"/>' +
    '<rect x="168" y="136" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="152" width="10" height="10" fill="#111827"/>' +
    '<rect x="120" y="152" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="152" width="10" height="10" fill="#111827"/>' +
    '<rect x="152" y="152" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="168" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="168" width="10" height="10" fill="#111827"/>' +
    '<rect x="168" y="168" width="10" height="10" fill="#111827"/>' +
    '<rect x="88" y="184" width="10" height="10" fill="#111827"/>' +
    '<rect x="104" y="184" width="10" height="10" fill="#111827"/>' +
    '<rect x="136" y="184" width="10" height="10" fill="#111827"/>' +
    '<rect x="152" y="184" width="10" height="10" fill="#111827"/>' +
  '</svg>'
)
