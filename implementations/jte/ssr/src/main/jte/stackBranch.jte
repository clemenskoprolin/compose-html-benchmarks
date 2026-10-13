@param int depth
<div>@if(depth == 0)<span class="foo" data-testid="stack">deep stack</span>@else@template.stackBranch(depth - 1)@endif</div>