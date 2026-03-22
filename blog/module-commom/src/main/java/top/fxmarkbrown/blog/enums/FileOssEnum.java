package top.fxmarkbrown.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum FileOssEnum {
    QINIU("qiniu"),

    ALI("ali"),

    TENCENT("tencent"),

    LOCAL("local");

    private String value;

}
