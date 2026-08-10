/**
 * Plain-language names and descriptions for every AI instruction (template).
 * Derived from what each seeded prompt actually does — keep in sync when a new
 * template key is added. Unknown keys get a humanized fallback name.
 */

export interface TemplateInfo {
  name: string;
  what: string;
  category: Category;
}

export type Category = 'AI pipeline' | 'Checklists' | 'Translation & language' | 'System replies';

export const CATEGORY_ORDER: Category[] = [
  'AI pipeline', 'Checklists', 'Translation & language', 'System replies',
];

export const TEMPLATE_INFO: Record<string, TemplateInfo> = {
  'ai.analyze_conversation': {
    name: 'Conversation analysis',
    what: 'Runs on every customer message — produces the sentiment scores, the running summary and the suggested reply the agent sees.',
    category: 'AI pipeline',
  },
  'ai.analyze_conversation_with_context': {
    name: 'Analysis with knowledge',
    what: 'Same analysis, used when knowledge-base or customer data was found, so the reply can quote real facts.',
    category: 'AI pipeline',
  },
  'ai.analyze_conversation_with_checklist': {
    name: 'Analysis with checklist',
    what: 'Analysis used when the customer’s topic has a matching checklist (fee waiver, billing…) so the suggestion follows the checklist steps.',
    category: 'AI pipeline',
  },
  'ai.analyze_text': {
    name: 'Single message analysis',
    what: 'Analyzes one standalone piece of text for sentiment when there is no full conversation.',
    category: 'AI pipeline',
  },
  'ai.detect_operation': {
    name: 'Topic classifier',
    what: 'Decides which topic (request type) the customer’s message is about. The topic list and rules are filled in automatically from the request types you manage on the Setup page.',
    category: 'AI pipeline',
  },
  'ai.overall_sentiment': {
    name: 'Overall sentiment',
    what: 'Looks at the whole conversation and produces one overall mood score for the customer.',
    category: 'AI pipeline',
  },
  'ai.regenerate_suggestions': {
    name: 'Fresh reply suggestions',
    what: 'Runs when the agent asks for a different suggestion — writes a new reply without repeating the previous one.',
    category: 'AI pipeline',
  },
  'ai.regenerate_suggestions_with_context': {
    name: 'Fresh suggestions with checklist',
    what: 'The “give me another suggestion” version used when a checklist and customer data are in play.',
    category: 'AI pipeline',
  },
  'ai.follow_up_check': {
    name: 'Follow-up check',
    what: 'Runs when a conversation ends — decides whether the customer needs a follow-up call and writes the wrap-up summary.',
    category: 'AI pipeline',
  },
  'ai.compliance': {
    name: 'Compliance scorecard',
    what: 'Reviews only the agent’s messages after a conversation: greeting, empathy, clarity, T&C mention, sign-off.',
    category: 'AI pipeline',
  },
  'rag.suggestions': {
    name: 'Knowledge-based reply',
    what: 'Writes the suggested reply from knowledge-base search results and customer data — instructs the AI to answer only from that material.',
    category: 'AI pipeline',
  },
  'ai.detect_language': {
    name: 'Language detection',
    what: 'Identifies which language the customer wrote in, so the system knows whether to translate.',
    category: 'Translation & language',
  },
  'ai.translate_to_english': {
    name: 'Translate to English',
    what: 'Translates the customer’s message into English before analysis (all analysis runs on English text).',
    category: 'Translation & language',
  },
  'ai.translate_from_english': {
    name: 'Translate from English',
    what: 'Translates the finished reply suggestions back into the customer’s own language.',
    category: 'Translation & language',
  },
  'checklist.fee_waiver': {
    name: 'Fee waiver checklist',
    what: 'Step-by-step guidance injected when a customer asks about waiving credit-card fees: spend thresholds, activation rules, reversal options.',
    category: 'Checklists',
  },
  'checklist.home_loan_closure': {
    name: 'Home loan closure checklist',
    what: 'Guidance injected when a customer wants to close or pay off a home loan: payoff steps and the email to contact.',
    category: 'Checklists',
  },
  'checklist.billing': {
    name: 'Card billing checklist',
    what: 'Guidance for card billing questions (balance, due date, blocked card) — states only what the customer’s record shows, never invents amounts.',
    category: 'Checklists',
  },
  'checklist.policy': {
    name: 'Policy inquiry checklist',
    what: 'Guidance when a customer asks about insurance policies: identity verification, answering from the policy data on file.',
    category: 'Checklists',
  },
  'checklist.claims': {
    name: 'Claims checklist',
    what: 'Guidance for insurance-claim questions: identity checks and reporting claim statuses from the records.',
    category: 'Checklists',
  },
  'checklist.telco': {
    name: 'Telco plans checklist',
    what: 'Guidance for mobile customers asking about their plan, data balance or top-ups — answers only from the customer’s plan data.',
    category: 'Checklists',
  },
  'system.no_knowledge_reply': {
    name: 'No-answer fallback',
    what: 'The exact sentence the agent gets when the knowledge base has nothing relevant. Plain text sent as-is — not an AI instruction.',
    category: 'System replies',
  },
};

/** "checklist.room_booking" -> "Checklist: Room booking" for keys not in the map. */
export function friendlyName(key: string): string {
  const known = TEMPLATE_INFO[key];
  if (known) return known.name;
  const [prefix, ...rest] = key.split('.');
  const words = rest.join(' ').replace(/_/g, ' ');
  const cap = (s: string) => (s ? s[0].toUpperCase() + s.slice(1) : s);
  return rest.length ? `${cap(prefix)}: ${cap(words)}` : cap(words || key);
}

export function templateCategory(key: string): Category {
  return TEMPLATE_INFO[key]?.category
    ?? (key.startsWith('checklist.') ? 'Checklists' : 'AI pipeline');
}
