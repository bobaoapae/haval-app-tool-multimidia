/**
 * createElement - A React-like utility for creating DOM elements
 * @param {string} tagName - The HTML tag name (e.g., 'div', 'span', 'button')
 * @param {Object} props - Element properties and attributes
 * @param {...(string|HTMLElement)} children - Child elements or text content
 * @returns {HTMLElement} The created DOM element
 */
export function createElement(tagName, props = {}) {
    // Create the element
    const element = document.createElement(tagName);
    const children = props.children || [];
    
    // Handle props
    if (props) {
        // Handle className (convert to class)
        if (props.className) {
            element.className = props.className;
            delete props.className;
        }
        
        // Handle style object
        if (props.style && typeof props.style === 'object') {
            Object.assign(element.style, props.style);
            delete props.style;
        }
        
        // Handle event listeners (props starting with 'on')
        Object.keys(props).forEach(key => {
            if (key.startsWith('on') && typeof props[key] === 'function') {
                const eventName = key.slice(2).toLowerCase();
                element.addEventListener(eventName, props[key]);
                delete props[key];
            }
        });
        
        // Handle other attributes
        Object.keys(props).forEach(key => {
            if (props[key] !== undefined && props[key] !== null) {
                element.setAttribute(key, props[key]);
            }
        });
    }
    
    // Handle children
    children.forEach(child => {
        if (child === null || child === undefined) {
            return;
        }
        
        if (typeof child === 'string' || typeof child === 'number') {
            element.appendChild(document.createTextNode(child));
        } else if (child instanceof HTMLElement) {
            element.appendChild(child);
        } else if (Array.isArray(child)) {
            child.forEach(nestedChild => {
                if (nestedChild !== null && nestedChild !== undefined) {
                    if (typeof nestedChild === 'string' || typeof nestedChild === 'number') {
                        element.appendChild(document.createTextNode(nestedChild));
                    } else if (nestedChild instanceof HTMLElement) {
                        element.appendChild(nestedChild);
                    }
                }
            });
        }
    });
    
    return element;
}

/**
 * Shorthand functions for common elements
 */
export const div = (props, ...children) => createElement('div', props, ...children);
export const span = (props, ...children) => createElement('span', props, ...children);
export const button = (props, ...children) => createElement('button', props, ...children);
export const img = (props) => createElement('img', props);
export const p = (props, ...children) => createElement('p', props, ...children);
export const h1 = (props, ...children) => createElement('h1', props, ...children);
export const h2 = (props, ...children) => createElement('h2', props, ...children);
export const h3 = (props, ...children) => createElement('h3', props, ...children);

/**
 * Utility for creating elements with common patterns
 */
export const createElementWithClasses = (tagName, className, props = {}, ...children) => {
    return createElement(tagName, { ...props, className }, ...children);
};

/**
 * Utility for creating styled elements
 */
export const createStyledElement = (tagName, styles, props = {}, ...children) => {
    return createElement(tagName, { ...props, style: styles }, ...children);
};
