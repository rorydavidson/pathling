/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

import * as React from "react";
import { useState } from "react";

import {
  ElementNode,
  getResolvedPath,
  getResource,
  getReverseReferences,
  resourceTree
} from "../fhir/ResourceTree";
import Resource from "./Resource";
import ContainedElements from "./ContainedElements";
import ReverseReference from "./ReverseReference";
import UnsupportedReference from "./UnsupportedReference";
import TreeNodeTooltip from "./TreeNodeTooltip";
import "./style/Reference.scss";

interface Props extends ElementNode {
  parentPath: string;
}

function Reference(props: Props) {
  const { name, type, path, definition, referenceTypes, parentPath } = props,
    resolvedPath = getResolvedPath(parentPath, path),
    unsupported =
      referenceTypes.length === 1 && !(referenceTypes[0] in resourceTree),
    [isExpanded, setExpanded] = useState(false);

  const openContextMenu = () => {};

  const renderContains = () =>
    referenceTypes.length > 1 ? renderResources() : renderContainsDirectly();

  const renderResources = () =>
    referenceTypes
      .filter(referenceType => referenceType in resourceTree)
      .map((referenceType, i) => {
        const newParentPath = `${resolvedPath}.resolve(${referenceType})`;
        return (
          <Resource
            {...getResource(referenceType)}
            key={i}
            name={referenceType}
            parentPath={newParentPath}
          />
        );
      });

  const renderContainsDirectly = () => {
    const referenceType = referenceTypes[0],
      contains = getResource(referenceType).contains,
      newParentPath = `${resolvedPath}.resolve()`,
      reverseReferenceNodes = getReverseReferences(referenceType).map(
        (node, i) => <ReverseReference {...node} key={i + 1} />
      );
    return [
      <ContainedElements nodes={contains} parentPath={newParentPath} key={0} />
    ].concat(reverseReferenceNodes);
  };

  return unsupported ? (
    <UnsupportedReference {...props} />
  ) : (
    <li className="reference">
      <TreeNodeTooltip
        type={type}
        definition={definition}
        referenceTypes={referenceTypes}
      >
        <span
          className={isExpanded ? "caret-open" : "caret-closed"}
          onClick={() => setExpanded(!isExpanded)}
        />
        <span className="icon" />
        <span className="label">{name}</span>
      </TreeNodeTooltip>
      {isExpanded ? <ol className="contains">{renderContains()}</ol> : null}
    </li>
  );
}

export default Reference;
