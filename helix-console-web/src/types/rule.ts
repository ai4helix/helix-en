/**
 * Rule condition AST tree node (aligned with backend RuleConditionNodeDTO).
 *
 * The engine stores rule conditions as an adjacency list (parent_id / node_type
 * in t_rule_condition), supporting AND/OR/NOT nesting at any depth; the frontend
 * X6 renders conditions as a subtree graph based on this.
 *
 * nodeType: 1=LEAF (leaf condition) / 2=AND / 3=OR / 4=NOT.
 */
export interface RuleConditionNode {
  id?: number
  nodeType: number
  fieldCode?: string
  operator?: string
  value?: string
  children?: RuleConditionNode[]
}

export const NODE_TYPE = {
  LEAF: 1,
  AND: 2,
  OR: 3,
  NOT: 4
} as const

/** Display label for a logic group (AND/OR/NOT) */
export function groupLabel(nodeType: number): string {
  switch (nodeType) {
    case NODE_TYPE.AND:
      return 'AND'
    case NODE_TYPE.OR:
      return 'OR'
    case NODE_TYPE.NOT:
      return 'NOT (Inverted)'
    default:
      return 'Group'
  }
}
