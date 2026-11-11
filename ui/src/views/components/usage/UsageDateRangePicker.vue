<script setup lang="ts">
import { getLocalTimeZone, parseDate, today, type DateValue } from '@internationalized/date'
import {
  PopoverContent,
  PopoverPortal,
  PopoverRoot,
  PopoverTrigger,
  RangeCalendarCell,
  RangeCalendarCellTrigger,
  RangeCalendarGrid,
  RangeCalendarGridBody,
  RangeCalendarGridHead,
  RangeCalendarGridRow,
  RangeCalendarHeadCell,
  RangeCalendarNext,
  RangeCalendarPrev,
  RangeCalendarRoot,
  type DateRange,
} from 'reka-ui'
import { computed, nextTick, shallowRef, watch } from 'vue'
import MingcuteCalendarLine from '~icons/mingcute/calendar-line'
import MingcuteLeftLine from '~icons/mingcute/left-line'
import MingcuteRightLine from '~icons/mingcute/right-line'

const props = defineProps<{
  fromDate?: string
  toDate?: string
}>()

const emit = defineEmits<{
  (event: 'update', value: { fromDate?: string; toDate?: string }): void
}>()

const open = shallowRef(false)
const selection = shallowRef<DateRange>({ start: undefined, end: undefined })
const maxValue = today(getLocalTimeZone())
// Two-month view: keep the end month (or current month) on the right
const placeholder = shallowRef<DateValue>(maxValue.subtract({ months: 1 }))

let syncing = false

function toCalendarDate(text?: string): DateValue | undefined {
  if (!text) return undefined
  try {
    return parseDate(text)
  } catch {
    return undefined
  }
}

function toText(value?: DateValue) {
  return value ? value.toString().slice(0, 10) : undefined
}

watch(open, async (isOpen) => {
  if (!isOpen) return
  syncing = true
  const end = toCalendarDate(props.toDate)
  selection.value = { start: toCalendarDate(props.fromDate), end }
  placeholder.value = (end ?? today(getLocalTimeZone())).subtract({ months: 1 })
  await nextTick()
  syncing = false
})

watch(selection, (range) => {
  if (syncing || !range?.start || !range?.end) return
  emit('update', { fromDate: toText(range.start), toDate: toText(range.end) })
  open.value = false
})

const label = computed(() =>
  props.fromDate && props.toDate ? `${props.fromDate} 至 ${props.toDate}` : '选择起止日期',
)
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger class=":uno: usage-date-trigger" aria-label="自定义起止日期">
      <MingcuteCalendarLine class=":uno: shrink-0 text-gray-400" />
      <span :class="fromDate && toDate ? undefined : ':uno: text-gray-400'">{{ label }}</span>
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent align="start" :side-offset="4">
        <div class=":uno: usage-date-popover">
          <RangeCalendarRoot
            v-model="selection"
            v-model:placeholder="placeholder"
            v-slot="{ weekDays, grid }"
            locale="zh-CN"
            :week-starts-on="1"
            weekday-format="narrow"
            :number-of-months="2"
            fixed-weeks
            :max-value="maxValue"
            prevent-deselect
          >
            <div class=":uno: usage-cal">
              <RangeCalendarPrev class=":uno: usage-cal-nav" aria-label="上一月">
                <MingcuteLeftLine />
              </RangeCalendarPrev>
              <div
                v-for="month in grid"
                :key="month.value.toString()"
                class=":uno: usage-cal-month"
              >
                <div class=":uno: usage-cal-title">
                  {{ month.value.year }} 年 {{ month.value.month }} 月
                </div>
                <RangeCalendarGrid class=":uno: usage-cal-grid">
                  <RangeCalendarGridHead>
                    <RangeCalendarGridRow>
                      <RangeCalendarHeadCell
                        v-for="day in weekDays"
                        :key="day"
                        class=":uno: usage-cal-weekday"
                        >{{ day }}</RangeCalendarHeadCell
                      >
                    </RangeCalendarGridRow>
                  </RangeCalendarGridHead>
                  <RangeCalendarGridBody>
                    <RangeCalendarGridRow
                      v-for="(weekDates, index) in month.rows"
                      :key="`week-${index}`"
                    >
                      <RangeCalendarCell
                        v-for="weekDate in weekDates"
                        :key="weekDate.toString()"
                        :date="weekDate"
                        class=":uno: usage-cal-cell"
                      >
                        <RangeCalendarCellTrigger
                          :day="weekDate"
                          :month="month.value"
                          class=":uno: usage-cal-trigger"
                        />
                      </RangeCalendarCell>
                    </RangeCalendarGridRow>
                  </RangeCalendarGridBody>
                </RangeCalendarGrid>
              </div>
              <RangeCalendarNext class=":uno: usage-cal-nav" aria-label="下一月">
                <MingcuteRightLine />
              </RangeCalendarNext>
            </div>
          </RangeCalendarRoot>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<style scoped>
.usage-date-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: white;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
  white-space: nowrap;
}
.usage-date-trigger:hover {
  border-color: #d1d5db;
}
.usage-date-trigger:focus-visible {
  border-color: #9ca3af;
  outline: none;
  box-shadow: 0 0 0 1px #e5e7eb;
}
.usage-date-popover {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow:
    0 10px 15px -3px rgb(0 0 0 / 0.1),
    0 4px 6px -4px rgb(0 0 0 / 0.1);
  padding: 12px;
}
.usage-cal {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.usage-cal-month {
  flex: 1 1 0;
  min-width: 0;
}
.usage-cal-title {
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
}
.usage-cal-nav {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
}
.usage-cal-nav:hover {
  background: #f3f4f6;
  color: #111827;
}
.usage-cal-grid {
  border-collapse: collapse;
}
.usage-cal-weekday {
  width: 30px;
  height: 26px;
  font-size: 10.5px;
  font-weight: 400;
  color: #9ca3af;
  text-align: center;
}
.usage-cal-cell {
  padding: 0;
  text-align: center;
}
.usage-cal-trigger {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
  font-variant-numeric: tabular-nums;
}
.usage-cal-trigger:hover:not([data-disabled]):not([data-selection-start]):not(
    [data-selection-end]
  ) {
  background: #f3f4f6;
}
.usage-cal-trigger[data-outside-view] {
  color: #d1d5db;
}
.usage-cal-trigger[data-today]:not([data-selection-start]):not([data-selection-end]) {
  font-weight: 600;
  color: #4CCBA0;
}
.usage-cal-trigger[data-highlighted],
.usage-cal-trigger[data-selected] {
  background: #ddf5ec;
  border-radius: 0;
}
.usage-cal-trigger[data-selection-start],
.usage-cal-trigger[data-selection-end] {
  background: #4CCBA0;
  color: #fff;
}
.usage-cal-trigger[data-selection-start] {
  border-radius: 6px 0 0 6px;
}
.usage-cal-trigger[data-selection-end] {
  border-radius: 0 6px 6px 0;
}
.usage-cal-trigger[data-selection-start][data-selection-end] {
  border-radius: 6px;
}
.usage-cal-trigger[data-disabled] {
  color: #d1d5db;
  cursor: not-allowed;
}
.usage-cal-trigger:focus-visible {
  outline: 2px solid #9ca3af;
  outline-offset: -2px;
}
</style>
