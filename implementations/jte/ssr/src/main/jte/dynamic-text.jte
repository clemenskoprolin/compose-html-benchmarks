@import benchmarks.model.TextData
@param TextData data
<div>@for(var item : data.getItems())<div><span class="foo" data-testid="foo">${item.getFoo()}</span><span class="bar" data-testid="bar">${item.getBar()}</span></div>@endfor</div>