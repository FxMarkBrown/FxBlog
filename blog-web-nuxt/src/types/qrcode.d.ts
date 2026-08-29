// qrcode 包未自带类型声明，且不允许新增 @types 依赖，
// 这里按实际用到的 API（toDataURL）补充最小类型声明。
declare module 'qrcode' {
  export interface QRCodeColorOptions {
    dark?: string
    light?: string
  }

  export interface QRCodeToDataURLOptions {
    margin?: number
    width?: number
    color?: QRCodeColorOptions
  }

  export function toDataURL(text: string, options?: QRCodeToDataURLOptions): Promise<string>

  const QRCode: {
    toDataURL: typeof toDataURL
  }
  export default QRCode
}
