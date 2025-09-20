package com.example.inventorypda;

// 装柜明细数据模型（对应数据库表：流水号+装柜流水）
public class CabinetDetail {
    private String serialNo;      // 流水号（已有数据，非空）
    private String cabinetFlow;   // 装柜流水（可空，待扫描写入）

    public CabinetDetail(String serialNo, String cabinetFlow) {
        this.serialNo = serialNo;
        this.cabinetFlow = cabinetFlow;
    }

    // Getter（用于适配列表展示和逻辑判断）
    public String getSerialNo() {
        return serialNo;
    }

    public String getCabinetFlow() {
        return cabinetFlow == null ? "未填写" : cabinetFlow;
    }
}