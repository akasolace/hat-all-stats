import React from 'react';
import { Translation } from 'react-i18next'
import '../i18n'
import './ModelTableTh.css'
import { SortingDirection } from '../rest/StatisticsParameters';

interface SortingState {
    callback: (sortBy: string) => void,
    currentSorting: string,
    sortingDirection: SortingDirection
}

interface ThProps {
    title: string,
    sortingField: string,
    sortingState: SortingState
}

class ModelTableTh extends React.Component<ThProps> {
    render() {
        return <Translation>
            {(t, { i18n }) =>
                <th className="model_table_header value" onClick={() => this.props.sortingState.callback(this.props.sortingField)}>
                    {t(this.props.title)}
                    {(this.props.sortingField === this.props.sortingState.currentSorting && this.props.sortingState.sortingDirection === SortingDirection.DESC) ? "↓" : ""}
                    {(this.props.sortingField === this.props.sortingState.currentSorting && this.props.sortingState.sortingDirection === SortingDirection.ASC) ? "↑" : ""}
                </th>
            }
        </Translation>
    }
}

export default ModelTableTh;