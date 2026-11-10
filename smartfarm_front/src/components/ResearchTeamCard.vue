<template>
  <section
    class="research-card"
    aria-label="茂林学村AI农业物联网监测系统主要研究成员"
  >
    <div class="card-aura" aria-hidden="true"></div>

    <header class="institution">
      <img :src="informaticsLogo" alt="厦门大学信息工程学院院徽" />
      <div class="institution-copy">
        <strong>厦门大学信息学院</strong>
        <span>SCHOOL OF INFORMATICS</span>
      </div>
    </header>

    <p class="team-title">
      <span>茂林学村 AI 农业</span>
      <span>物联网监测系统主要研究成员</span>
    </p>

    <div class="member-list" role="list" aria-label="研究成员名单">
      <div v-for="member in members" :key="member" class="member" role="listitem">
        <i aria-hidden="true"></i>
        <span>{{ member }}</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import informaticsLogo from '../assets/xmu-informatics-logo.jpg'

const members = ['郭程', '吴史焜', '赖文瑜', '黄胜鑫']
</script>

<style scoped>
.research-card {
  position: relative;
  flex: none;
  margin: 8px 9px 10px;
  overflow: hidden;
  color: #dceeff;
  border: 1px solid rgba(55, 178, 229, 0.54);
  border-radius: 18px 18px 12px 12px;
  background:
    linear-gradient(155deg, rgba(14, 51, 94, 0.9), rgba(6, 25, 57, 0.96)),
    radial-gradient(circle at 10% 0%, rgba(38, 201, 217, 0.2), transparent 48%);
  box-shadow:
    0 10px 24px rgba(0, 7, 26, 0.34),
    inset 0 0 22px rgba(31, 124, 185, 0.08);
}

.research-card::before,
.research-card::after {
  content: '';
  position: absolute;
  width: 28px;
  height: 12px;
  pointer-events: none;
}
.research-card::before {
  left: -1px;
  top: -1px;
  border-top: 2px solid #52e4c0;
  border-left: 2px solid #52e4c0;
  border-radius: 18px 0 0;
}
.research-card::after {
  right: -1px;
  bottom: -1px;
  border-right: 2px solid #4588ff;
  border-bottom: 2px solid #4588ff;
  border-radius: 0 0 12px;
}

.card-aura {
  position: absolute;
  top: -58px;
  right: -48px;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: rgba(38, 170, 255, 0.13);
  filter: blur(18px);
  pointer-events: none;
}

.institution {
  position: relative;
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 12px 11px 10px;
  border-bottom: 1px solid rgba(70, 139, 205, 0.34);
}
.institution img {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border: 1px solid rgba(100, 213, 255, 0.7);
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 13px rgba(43, 163, 235, 0.35);
}
.institution-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.institution-copy strong {
  color: #f2f8ff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  white-space: normal;
}
.institution-copy span {
  color: #5f93bc;
  font-size: 7px;
  letter-spacing: 0.7px;
  white-space: nowrap;
}

.team-title {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 10px 8px;
  color: #a9daf4;
  font-size: 10px;
  line-height: 1.45;
  text-align: center;
  letter-spacing: 0.3px;
  border-bottom: 1px solid rgba(70, 139, 205, 0.3);
  background: rgba(17, 63, 108, 0.18);
}

.member-list {
  position: relative;
  padding: 2px 10px 7px;
}
.member {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 30px;
  color: #a9c9e8;
  border-bottom: 1px solid rgba(54, 112, 169, 0.23);
  font-size: 11px;
  transition: color 0.18s, background 0.18s;
}
.member:last-child {
  border-bottom: 0;
}
.member:hover {
  color: #edf8ff;
  background: rgba(31, 105, 164, 0.12);
}
.member i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #4ecfc3;
  box-shadow: 0 0 6px rgba(78, 207, 195, 0.75);
}
</style>
