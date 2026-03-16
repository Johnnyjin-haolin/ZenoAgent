<template>
  <a-modal
    :open="true"
    :title="skill ? t('skill.modal.editTitle') : t('skill.modal.createTitle')"
    :width="760"
    :confirm-loading="saving"
    :ok-text="t('skill.modal.save')"
    :cancel-text="t('skill.modal.cancel')"
    class="tech-modal skill-edit-modal"
    @ok="handleSave"
    @cancel="emit('close')"
  >
    <div class="skill-form">
      <div class="form-item">
        <label class="form-label">{{ t('skill.modal.nameLabel') }} <span class="required">*</span></label>
        <a-input
          v-model:value="form.name"
          :placeholder="t('skill.modal.namePlaceholder')"
          :maxlength="128"
        />
      </div>

      <div class="form-item">
        <label class="form-label">
          {{ t('skill.modal.summaryLabel') }} <span class="required">*</span>
          <span class="label-hint">{{ t('skill.modal.summaryHint') }}</span>
        </label>
        <a-textarea
          v-model:value="form.summary"
          :placeholder="t('skill.modal.summaryPlaceholder')"
          :rows="2"
          :maxlength="512"
          show-count
        />
      </div>

      <div class="form-item">
        <label class="form-label">{{ t('skill.modal.tagsLabel') }}</label>
        <a-select
          v-model:value="form.tags"
          mode="tags"
          :placeholder="t('skill.modal.tagsPlaceholder')"
          :token-separators="[',']"
          :dropdown-class-name="'tech-select-dropdown'"
        />
      </div>

      <div class="form-item">
        <label class="form-label">
          {{ t('skill.modal.contentLabel') }} <span class="required">*</span>
          <span class="label-hint">{{ t('skill.modal.contentHint') }}</span>
        </label>
        <a-textarea
          v-model:value="form.content"
          :placeholder="t('skill.modal.contentPlaceholder')"
          :rows="14"
          class="content-textarea"
        />
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watchEffect } from 'vue';
import { message } from 'ant-design-vue';
import { useI18n } from 'vue-i18n';
import { createSkill, updateSkill } from '@/api/agent-skill.api';
import type { AgentSkill } from '@/api/agent-skill.api';

const { t } = useI18n();

interface Props {
  skill?: AgentSkill | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const saving = ref(false);

const form = reactive({
  name: '',
  summary: '',
  content: '',
  tags: [] as string[],
});

watchEffect(() => {
  if (props.skill) {
    form.name = props.skill.name;
    form.summary = props.skill.summary;
    form.content = props.skill.content;
    form.tags = [...(props.skill.tags || [])];
  } else {
    form.name = '';
    form.summary = '';
    form.content = '';
    form.tags = [];
  }
});

async function handleSave() {
  if (!form.name.trim()) {
    message.warning(t('skill.modal.nameRequired'));
    return;
  }
  if (!form.summary.trim()) {
    message.warning(t('skill.modal.summaryRequired'));
    return;
  }
  if (!form.content.trim()) {
    message.warning(t('skill.modal.contentRequired'));
    return;
  }

  saving.value = true;
  try {
    if (props.skill) {
      await updateSkill(props.skill.id, {
        name: form.name,
        summary: form.summary,
        content: form.content,
        tags: form.tags,
      });
      message.success(t('skill.modal.updateSuccess'));
    } else {
      await createSkill({
        name: form.name,
        summary: form.summary,
        content: form.content,
        tags: form.tags,
      });
      message.success(t('skill.modal.createSuccess'));
    }
    emit('saved');
  } catch {
    message.error(props.skill ? t('skill.modal.updateFailed') : t('skill.modal.createFailed'));
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="less">
.skill-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 6px;
}

.required {
  color: #ef4444;
}

.label-hint {
  font-size: 11px;
  color: #475569;
  font-weight: 400;
}

:deep(.content-textarea) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
