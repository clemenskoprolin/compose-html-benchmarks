import React from "react";

function TextPair({ item }) {
  return <div><span className="foo" data-testid="foo">{item.foo}</span><span className="bar" data-testid="bar">{item.bar}</span></div>;
}

export function DynamicText({ data }) {
  return <div>{data.items.map((item, index) => <TextPair key={index} item={item} />)}</div>;
}
