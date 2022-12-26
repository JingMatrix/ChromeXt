(function () {
  if (!document.querySelector("#eruda")) {
    return;
  }
  const erudaroot = document.querySelector("#eruda").shadowRoot;
  const style = document.createElement("style");
  style.setAttribute("type", "text/css");
  style.textContent = `
	[class^='eruda-icon-']:before {
		font-size: 14px;
	}
	.eruda-icon-arrow-left:before {
	  content: '←';
	}
	.eruda-icon-arrow-right:before {
	  content: '→';
	}
	.eruda-icon-clear:before {
	  content: '✖';
	  font-size: 17px;
	}
	.eruda-icon-compress:before {
	  content: '🗎';
	}
	.eruda-icon-copy:before {
	  content: '⎘ ';
	  font-size: 16px;
	  font-weight: bold;
	}
	.eruda-icon-delete:before {
	  content: '⌫';
	  font-weight: bold;
	}
	.eruda-icon-expand:before {
	  content: '⌄';
	}
	.eruda-icon-eye:before {
	  content: '🧿';
	}
	.eruda-icon-filter:before {
	  content: '⭃';
      font-size: 19px;
      font-weight: bold;
      display: block;
      transform: rotate(90deg);
	}
	.eruda-icon-play:before {
	  content: '▷';
	}
	.eruda-icon-record:before {
	  content: '●';
	}
	.eruda-icon-refresh:before {
	  content: '↻';
	  font-size: 18px;
	  font-weight: bold;
	}
	.eruda-icon-reset:before {
	  content: '↺';
	}
	.eruda-icon-search:before {
	  content: '🔍';
	}
	.eruda-icon-select:before {
	  content: '➤';
	  font-size: 14px;
	  display: block;
	  transform: rotate(232deg);
	}
	.eruda-icon-tool:before {
	  content: '⚙';
	  font-size: 30px;
	}
	.luna-console-icon-error:before {
	  content: '✗';
	}
	.luna-console-icon-warn:before {
	  content: '⚠';
	}
	[class\$='icon-caret-right']:before,
	[class\$='icon-arrow-right']:before {
	  content: '▼';
	  font-size: 9px;
	  display: block;
	  transform: rotate(-0.25turn);
	}
	[class\$='icon-caret-down']:before,
	[class\$='icon-arrow-down']:before {
	  content: '▼';
	  font-size: 9px;
	}
	`;
  erudaroot.append(style);
})();
