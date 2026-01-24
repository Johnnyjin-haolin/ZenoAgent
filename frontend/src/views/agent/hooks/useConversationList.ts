import { ref, watch, type Ref } from 'vue';
import { message } from 'ant-design-vue';
import { getConversations, updateConversationTitle } from '../agent.api';
import {
  DEFAULT_CONVERSATION_TITLE,
  MESSAGE_NEW_CONVERSATION_CREATED,
  TEMP_CONVERSATION_PREFIX,
} from '../agent.constants';
import type { ConversationInfo } from '../agent.types';

type ConversationView = ConversationInfo & { isTemporary?: boolean };

type UseConversationListOptions = {
  currentConversationId: Ref<string>;
  loadMessages: (conversationId: string) => Promise<void>;
  clearMessages: () => void;
  scrollToBottom: () => Promise<void>;
  setUserInput: (value: string) => void;
};

export const useConversationList = (options: UseConversationListOptions) => {
  const { currentConversationId, loadMessages, clearMessages, scrollToBottom, setUserInput } = options;

  const conversations = ref<ConversationView[]>([]);
  const showSlide = ref(true);
  const slideCollapsed = ref(false);
  const temporaryConversationId = ref<string | null>(null);
  const temporaryHasMessages = ref(false);

  const loadConversations = async () => {
    try {
      const result = await getConversations();
      conversations.value = result;

      if (result.length > 0 && !currentConversationId.value) {
        currentConversationId.value = result[0].id;
        await loadMessages(result[0].id);
        await scrollToBottom();
      }
    } catch (error) {
      console.error('加载会话列表失败:', error);
    }
  };

  const handleSelectConversation = async (conversation: ConversationInfo) => {
    if (temporaryConversationId.value === currentConversationId.value && !temporaryHasMessages.value) {
      const index = conversations.value.findIndex((c) => c.id === temporaryConversationId.value);
      if (index !== -1) {
        conversations.value.splice(index, 1);
      }
      temporaryConversationId.value = null;
    }

    currentConversationId.value = conversation.id;
    clearMessages();

    await loadMessages(conversation.id);
    await scrollToBottom();
  };

  const handleNewConversation = () => {
    if (temporaryConversationId.value && temporaryHasMessages.value) {
      currentConversationId.value = temporaryConversationId.value;
      setUserInput('');
      return;
    }

    if (temporaryConversationId.value && !temporaryHasMessages.value) {
      const index = conversations.value.findIndex((c) => c.id === temporaryConversationId.value);
      if (index !== -1) {
        conversations.value.splice(index, 1);
      }
    }

    const tempId = `${TEMP_CONVERSATION_PREFIX}${Date.now()}`;
    const tempConversation: ConversationView = {
      id: tempId,
      title: DEFAULT_CONVERSATION_TITLE,
      isEdit: false,
      disabled: true,
      isTemporary: true,
    };
    conversations.value.unshift(tempConversation);
    temporaryConversationId.value = tempId;
    temporaryHasMessages.value = false;

    currentConversationId.value = tempId;
    clearMessages();
    setUserInput('');
  };

  const handleUpdateConversation = async (conversation: ConversationInfo) => {
    try {
      const success = await updateConversationTitle(conversation.id, conversation.title);
      if (success) {
        message.success('更新成功');
        const index = conversations.value.findIndex((c) => c.id === conversation.id);
        if (index !== -1) {
          conversations.value[index].title = conversation.title;
        }
      } else {
        message.error('更新失败');
      }
    } catch (error) {
      console.error('更新会话失败:', error);
      message.error('更新失败');
    }
  };

  const handleDeleteConversation = (conversationId: string) => {
    const index = conversations.value.findIndex((c) => c.id === conversationId);
    if (index !== -1) {
      conversations.value.splice(index, 1);
    }

    if (currentConversationId.value === conversationId) {
      handleNewConversation();
    }
  };

  const toggleSlide = () => {
    slideCollapsed.value = !slideCollapsed.value;
  };

  const syncConversationTitle = async (conversationId: string) => {
    try {
      const result = await getConversations();
      const matched = result.find((item) => item.id === conversationId);
      if (matched) {
        const index = conversations.value.findIndex((c) => c.id === conversationId);
        if (index !== -1) {
          conversations.value[index].title = matched.title;
          conversations.value[index].modelId = matched.modelId;
          conversations.value[index].modelName = matched.modelName;
          conversations.value[index].messageCount = matched.messageCount;
        }
      }
    } catch (error) {
      console.error('同步会话标题失败:', error);
    }
  };

  watch(currentConversationId, async (newId, oldId) => {
    if (!newId || !oldId) return;
    if (oldId !== temporaryConversationId.value) return;

    const existingIndex = conversations.value.findIndex((c) => c.id === newId);
    const tempIndex = conversations.value.findIndex((c) => c.id === oldId);

    if (existingIndex !== -1) {
      if (tempIndex !== -1) {
        conversations.value.splice(tempIndex, 1);
      }
    } else if (tempIndex !== -1) {
      conversations.value[tempIndex].id = newId;
      conversations.value[tempIndex].isTemporary = false;
      conversations.value[tempIndex].disabled = false;
    }

    await syncConversationTitle(newId);

    temporaryConversationId.value = null;
    temporaryHasMessages.value = false;
  });

  const markTemporaryHasMessages = () => {
    if (temporaryConversationId.value === currentConversationId.value) {
      temporaryHasMessages.value = true;
    }
  };

  return {
    conversations,
    currentConversationId,
    showSlide,
    slideCollapsed,
    temporaryConversationId,
    temporaryHasMessages,
    loadConversations,
    handleSelectConversation,
    handleNewConversation,
    handleUpdateConversation,
    handleDeleteConversation,
    toggleSlide,
    markTemporaryHasMessages,
  };
};

