import { div } from '../utils/createElement.js';

export function createSeparatorElement() {
    return div({
        className: 'connector',
        children: [
            div({
                className: 'w-12 h-12 bg-blue-500 rounded-full mb-8',
            }),
            div({
                className: 'w-4 h-64 gradient-line',
            }),
            div({
                className: 'w-12 h-12 bg-blue-500 rounded-full mt-8',
            }), 
        ],
    })
}