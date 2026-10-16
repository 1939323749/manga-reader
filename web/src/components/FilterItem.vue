<script setup>
// One entry of a source's filter list; edits `filter.state` in place (the panel owns a draft copy).
const props = defineProps({ filter: { type: Object, required: true } })

const TRI = ['', '✓', '✕']
const TRI_LABEL = ['不限', '包含', '排除']

function cycle() {
  props.filter.state = ((props.filter.state || 0) + 1) % 3
}

function setSort(index) {
  props.filter.state = { index: Number(index), ascending: props.filter.state?.ascending ?? false }
}

function flipSort() {
  const s = props.filter.state || { index: 0, ascending: false }
  props.filter.state = { index: s.index, ascending: !s.ascending }
}
</script>

<template>
  <p v-if="filter.type === 'header'" class="header">{{ filter.name }}</p>

  <hr v-else-if="filter.type === 'separator'" class="sep" />

  <label v-else-if="filter.type === 'text'" class="field">
    <span>{{ filter.name }}</span>
    <input v-model="filter.state" type="text" />
  </label>

  <label v-else-if="filter.type === 'checkbox'" class="check">
    <input v-model="filter.state" type="checkbox" />
    <span>{{ filter.name }}</span>
  </label>

  <button
    v-else-if="filter.type === 'tristate'"
    type="button"
    class="tri"
    :class="['s' + (filter.state || 0)]"
    :title="TRI_LABEL[filter.state || 0]"
    :aria-label="`${filter.name}：${TRI_LABEL[filter.state || 0]}`"
    @click="cycle"
  >
    <span class="box" aria-hidden="true">{{ TRI[filter.state || 0] }}</span>
    <span>{{ filter.name }}</span>
  </button>

  <label v-else-if="filter.type === 'select'" class="field">
    <span>{{ filter.name }}</span>
    <select v-model.number="filter.state">
      <option v-for="(v, i) in filter.values" :key="i" :value="i">{{ v }}</option>
    </select>
  </label>

  <div v-else-if="filter.type === 'sort'" class="field">
    <span>{{ filter.name }}</span>
    <div class="sort">
      <select :value="filter.state?.index ?? ''" :aria-label="filter.name" @change="setSort($event.target.value)">
        <option v-if="!filter.state" value="" disabled>默认</option>
        <option v-for="(v, i) in filter.values" :key="i" :value="i">{{ v }}</option>
      </select>
      <button type="button" class="btn" :disabled="!filter.state" @click="flipSort">
        {{ filter.state?.ascending ? '升序 ↑' : '降序 ↓' }}
      </button>
    </div>
  </div>

  <details v-else-if="filter.type === 'group'" class="group">
    <summary>
      {{ filter.name }}
      <span v-if="filter.filters.some((c) => c.state && c.type !== 'select' && c.type !== 'text')" class="dot" aria-hidden="true"></span>
    </summary>
    <div class="children">
      <FilterItem v-for="c in filter.filters" :key="c.index" :filter="c" />
    </div>
  </details>
</template>

<style scoped>
.header {
  margin: 6px 0 0;
  font-size: var(--step--1);
  color: var(--pencil);
  grid-column: 1 / -1;
}

.sep {
  grid-column: 1 / -1;
  border: 0;
  border-top: 1px solid var(--tone);
  margin: 4px 0;
  width: 100%;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--step--1);
}

.field input,
.field select {
  width: 100%;
}

.sort {
  display: flex;
  gap: 6px;
}

.sort select {
  flex: 1;
}

.check,
.tri {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--step--1);
  text-align: left;
  min-width: 0;
}

.tri {
  border: 0;
  background: none;
  color: var(--ink);
  padding: 2px 0;
}

.tri .box {
  flex: none;
  width: 18px;
  height: 18px;
  border: 2px solid var(--ink);
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  line-height: 1;
}

.tri.s1 .box {
  background: var(--cobalt);
  border-color: var(--cobalt);
  color: #fff;
}

.tri.s2 .box {
  background: var(--danger);
  border-color: var(--danger);
  color: #fff;
}

.tri.s2 > span:last-child {
  text-decoration: line-through;
  color: var(--pencil);
}

.group {
  grid-column: 1 / -1;
  border: 2px solid var(--ink);
  padding: 0 12px;
}

.group summary {
  cursor: pointer;
  padding: 6px 0;
  font-weight: 600;
  font-size: var(--step--1);
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cobalt);
  margin-left: 6px;
  vertical-align: middle;
}

.children {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 4px 14px;
  padding: 4px 0 12px;
}
</style>
