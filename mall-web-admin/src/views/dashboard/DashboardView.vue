<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDashboardSummary, type DashboardSummary } from '@/api/dashboard'

const loading = ref(false)
const data = ref<DashboardSummary>({})

/** 金额格式化：分转元（后端 BigDecimal 序列化为 number） */
function yuan(v?: number) {
  return v == null ? '-' : `¥${Number(v).toFixed(2)}`
}

/** 7 天趋势最大销售额（柱状图比例） */
function maxSales() {
  return Math.max(1, ...(data.value.trend7d ?? []).map((r) => Number(r.salesAmount || 0)))
}

async function load() {
  loading.value = true
  try {
    data.value = await getDashboardSummary()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="dashboard-page">
    <el-card shadow="never" class="toolbar">
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      <span class="refresh-tip">指标来自各服务实时统计（Redis + DB），单项失败自动降级为空</span>
    </el-card>

    <!-- 今日概览 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">今日订单数</p>
          <p class="stat-value">{{ data.today?.orderCount ?? '-' }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">今日销售额</p>
          <p class="stat-value">{{ yuan(data.today?.salesAmount) }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">今日秒杀订单</p>
          <p class="stat-value">{{ data.today?.seckillOrderCount ?? '-' }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">实时在线会员</p>
          <p class="stat-value">{{ data.member?.online ?? '-' }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="second-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">今日日活（DAU）</p>
          <p class="stat-value">{{ data.member?.dau ?? '-' }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">今日签到人数</p>
          <p class="stat-value">{{ data.member?.checkinToday ?? '-' }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">今日新增会员</p>
          <p class="stat-value">{{ data.member?.newMembersToday ?? '-' }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">库存预警 SKU</p>
          <p class="stat-value warning-text">{{ data.warnings?.length ?? '-' }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="second-row">
      <!-- 近 7 天订单趋势（纯 CSS 柱状图） -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>近 7 天订单趋势（销售额）</template>
          <div v-if="data.trend7d?.length" class="trend-chart">
            <div v-for="row in data.trend7d" :key="row.date" class="trend-col">
              <div class="trend-bar" :style="{ height: `${Math.max(4, (Number(row.salesAmount || 0) / maxSales()) * 120)}px` }" />
              <p class="trend-val">{{ yuan(row.salesAmount) }}</p>
              <p class="trend-date">{{ row.date }}</p>
            </div>
          </div>
          <el-empty v-else description="暂无订单数据" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 销量榜 + 浏览榜 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>商品排行（销量 / 浏览量 Top 10）</template>
          <el-table :data="data.salesRank ?? []" size="small" border stripe>
            <el-table-column label="销量榜" min-width="180">
              <template #default="{ row, $index }">
                <span class="rank-no" :class="{ 'rank-top': $index < 3 }">{{ $index + 1 }}</span>
                {{ row.spuName || `SKU ${row.skuId}` }}
              </template>
            </el-table-column>
            <el-table-column label="销量" width="90">
              <template #default="{ row }">{{ row.sales }} 件</template>
            </el-table-column>
          </el-table>
          <el-table :data="data.viewsRank ?? []" size="small" border stripe class="rank-table">
            <el-table-column label="浏览榜" min-width="180">
              <template #default="{ row, $index }">
                <span class="rank-no" :class="{ 'rank-top': $index < 3 }">{{ $index + 1 }}</span>
                {{ row.spuName || `SPU ${row.spuId}` }}
              </template>
            </el-table-column>
            <el-table-column label="PV" width="90">
              <template #default="{ row }">{{ row.pv }} 次</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 库存预警明细 -->
    <el-card shadow="never" class="second-row">
      <template #header>库存预警明细（stock &lt; low_stock）</template>
      <el-table v-if="data.warnings?.length" :data="data.warnings" size="small" border stripe>
        <el-table-column prop="skuCode" label="SKU 编码" min-width="170" />
        <el-table-column label="当前库存" width="110">
          <template #default="{ row }">
            <el-tag type="danger">{{ row.stock }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lowStock" label="预警阈值" width="110" />
      </el-table>
      <el-empty v-else description="库存充足，无预警" :image-size="60" />
    </el-card>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.refresh-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.stat-value {
  margin: 8px 0 0;
  font-size: 26px;
  font-weight: 600;
}
.warning-text {
  color: #f56c6c;
}
.second-row {
  margin-top: 16px;
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 180px;
  padding: 0 8px;
}
.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}
.trend-bar {
  width: 28px;
  background: #409eff;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s;
}
.trend-val {
  margin: 6px 0 0;
  font-size: 11px;
  color: #606266;
}
.trend-date {
  margin: 2px 0 0;
  font-size: 11px;
  color: #909399;
}
.rank-table {
  margin-top: 12px;
}
.rank-no {
  display: inline-block;
  width: 18px;
  height: 18px;
  margin-right: 6px;
  line-height: 18px;
  text-align: center;
  border-radius: 3px;
  color: #fff;
  background: #c0c4cc;
  font-size: 12px;
}
.rank-top {
  background: #f56c6c;
}
</style>
