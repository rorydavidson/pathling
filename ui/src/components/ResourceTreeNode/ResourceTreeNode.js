/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

import React, { useState } from 'react'
import { connect } from 'react-redux'
import {
  MenuItem,
  Menu,
  ContextMenu,
  Popover,
  PopoverInteractionKind,
  Position,
} from '@blueprintjs/core'

import ElementTreeNode from '../ElementTreeNode'
import { resourceTree } from '../../fhir/ResourceTree'
import * as actions from '../../store/Actions'
import './ResourceTreeNode.less'

/**
 * Renders a tree node showing the elements available within a particular
 * resource type.
 *
 * @author John Grimes
 */
function ResourceTreeNode(props) {
  const { name, referencePath, addAggregation } = props,
    definition = resourceTree.get(name).get('definition'),
    [isExpanded, setExpanded] = useState(false)

  const openContextMenu = event => {
    const aggregationExpression = `${name}.count()`,
      aggregationLabel = aggregationExpression,
      aggregationMenuItem = (
        <MenuItem
          icon="trending-up"
          text={`Add "${aggregationExpression}" to aggregations`}
          onClick={() =>
            addAggregation({
              expression: aggregationExpression,
              label: aggregationLabel,
            })
          }
        />
      )

    ContextMenu.show(<Menu>{aggregationMenuItem}</Menu>, {
      left: event.clientX,
      top: event.clientY,
    })
  }

  const getReferencePath = node => {
    if (!referencePath) return node.get('path')
    const pathSuffix = node
      .get('path')
      .split('.')
      .slice(1)
      .join('.')
    return `${referencePath}.${pathSuffix}`
  }

  const renderChildren = () => {
    const childNodes = resourceTree.get(name).get('children'),
      elementTreeNodes = childNodes.map((node, i) => (
        <ElementTreeNode
          {...node.delete('children').toJS()}
          key={i}
          path={getReferencePath(node)}
          treePath={[name, 'children', i]}
          resourceOrComplexType={name}
        />
      ))
    return (
      <ol className="child-nodes bp3-tree-node-list">{elementTreeNodes}</ol>
    )
  }

  const renderActionIcon = () =>
    referencePath ? null : (
      <span
        className="bp3-tree-node-secondary-label bp3-icon-standard bp3-icon-arrow-right"
        onClick={openContextMenu}
      />
    )

  const renderNodeContent = () => (
    <div className="inner">
      <div className="bp3-tree-node-content">
        <span
          className={getCaretClasses()}
          onClick={() => setExpanded(!isExpanded)}
        />
        <span className="bp3-tree-node-icon bp3-icon-standard bp3-icon-cube" />
        <span className="name bp3-tree-node-label" onClick={openContextMenu}>
          {name}
        </span>
        {renderActionIcon()}
      </div>
      {isExpanded ? renderChildren() : null}
    </div>
  )

  const getNodeClasses = () =>
    isExpanded
      ? 'resource-tree-node bp3-tree-node bp3-tree-node-expanded'
      : 'resource-tree-node bp3-tree-node'

  const getCaretClasses = () =>
    isExpanded
      ? 'bp3-tree-node-caret bp3-tree-node-caret-open bp3-icon-standard'
      : 'bp3-tree-node-caret bp3-tree-node-caret-close bp3-icon-standard'

  return (
    <li className={getNodeClasses()}>
      {isExpanded ? (
        renderNodeContent()
      ) : (
        <Popover
          content={<div className="definition">{definition}</div>}
          position={Position.RIGHT}
          boundary={document.body}
          interactionKind={PopoverInteractionKind.HOVER}
          popoverClassName="bp3-dark"
          hoverOpenDelay={300}
        >
          {renderNodeContent()}
        </Popover>
      )}
    </li>
  )
}

export default connect(
  null,
  actions,
)(ResourceTreeNode)
