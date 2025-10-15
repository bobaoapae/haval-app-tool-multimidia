import { div as createDiv } from '../utils/createElement.js';

export function createFocusElementWithChildren(props) {
    props = props || {};
    var focused = props.focused || false;
    var className = props.className || '';
    var children = props.children || [];
    var customStyles = props.styles || {};
    
    var div = createDiv({
        className: 'focus-area',
        children: children,
        style: customStyles,
    });
    
    // Apply custom styles if provided
    for (var styleKey in customStyles) {
        if (customStyles.hasOwnProperty(styleKey)) {
            div.style[styleKey] = customStyles[styleKey];
        }
    }
    
    // Add custom className if provided
    if (className) {
        div.className = className;
    }
    
    // Focus styles with browser prefixes
    if (focused) {
        div.classList.add('transition-all', 'focus-active');
    }
    
    // Append children if provided
    if (Array.isArray(children)) {
        for (var i = 0; i < children.length; i++) {
            var child = children[i];
            if (child instanceof Node) {
                div.appendChild(child);
            }
        }
    }
    
    return div;
}