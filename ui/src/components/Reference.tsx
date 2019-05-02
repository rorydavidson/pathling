/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

import * as React from "react";

import {
  ElementNode,
  getResource,
  getReverseReferences,
  resourceTree
} from "../fhir/ResourceTree";
import Resource from "./Resource";
import ContainedElements from "./ContainedElements";
import ReverseReference from "./ReverseReference";
import UnsupportedReference from "./UnsupportedReference";
import "./style/Reference.scss";
import TreeNodeTooltip from "./TreeNodeTooltip";

interface Props extends ElementNode {}

function Reference(props: Props) {
  const { name, type, definition, referenceTypes } = props,
    unsupported =
      referenceTypes.length === 1 && !(referenceTypes[0] in resourceTree);

  const openContextMenu = () => {};

  // const renderContains = () =>
  //   referenceTypes.length > 1 ? renderResources() : renderContainsDirectly();
  //
  // const renderResources = () =>
  //   referenceTypes
  //     .filter(referenceType => referenceType in resourceTree)
  //     .map(referenceType => {
  //       return (
  //         <Resource {...getResource(referenceType)} name={referenceType} />
  //       );
  //     });
  //
  // const renderContainsDirectly = () => {
  //   const referenceType = referenceTypes[0],
  //     contains = getResource(referenceType).contains,
  //     reverseReferenceNodes = getReverseReferences(referenceType).map(node => (
  //       <ReverseReference {...node} />
  //     ));
  //   return [<ContainedElements nodes={contains} />].concat(
  //     reverseReferenceNodes
  //   );
  // };

  return unsupported ? (
    <UnsupportedReference {...props} />
  ) : (
    <li className="reference">
      <TreeNodeTooltip
        type={type}
        definition={definition}
        referenceTypes={referenceTypes}
      >
        <span className="caret" />
        <span className="icon" />
        <span className="label">{name}</span>
      </TreeNodeTooltip>
      {/*<ol className="contains">{renderContains()}</ol>*/}
    </li>
  );
}

export default Reference;
